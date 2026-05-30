/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public final class LobbyCommand implements SimpleCommand {

    private final VelocityNavigator plugin;
    private final java.util.concurrent.atomic.AtomicLong degradedCounter = new java.util.concurrent.atomic.AtomicLong(0);

    public LobbyCommand(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        Config config = plugin.config();
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MessageFormatter.render(config.messages().playerOnly()));
            return;
        }

        if (requiresPermission(config.commands().permission()) && !player.hasPermission(config.commands().permission())) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();

        // Check for direct connection from menu click event
        if (args.length >= 2 && "connect".equalsIgnoreCase(args[0])) {
            String targetServer = args[1];
            String token = args.length >= 3 ? args[2] : "";
            if (!plugin.consumeMenuToken(player, targetServer, token)) {
                player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                        Map.of("reason", "The lobby menu selection expired. Run /" + config.commands().primary() + " again.", "player", player.getUsername()), player));
                return;
            }
            connectFromMenuSelection(player, config, targetServer);
            return;
        }

        if (!hasBypassCooldown(player)) {
            OptionalLong secondsRemaining = plugin.cooldowns().secondsRemaining(player.getUniqueId());
            if (secondsRemaining.isPresent()) {
                player.sendMessage(MessageFormatter.render(config.messages().cooldown(), Map.of("time", String.valueOf(secondsRemaining.getAsLong())), player));
                return;
            }
        }

        // Apply cooldown pre-execution to block macro spam before the async route resolves.
        // If routing fails, the cooldown is cleared so the player is not penalized.
        plugin.cooldowns().apply(player.getUniqueId(), config.commands().cooldownSeconds());

        plugin.previewRoute(player)
                .thenAccept(decision -> handleDecision(player, config, decision, args))
                .exceptionally(throwable -> {
                    plugin.cooldowns().clear(player.getUniqueId());
                    player.sendMessage(Component.text("VelocityNavigator could not resolve a lobby right now.", NamedTextColor.RED));
                    plugin.logger().error("Failed to resolve /lobby for {}", player.getUsername(), throwable);
                    return null;
                });
    }

    private void connectFromMenuSelection(Player player, Config config, String targetServer) {
        plugin.previewRoute(player)
                .thenAccept(decision -> {
                    boolean stillAvailable = decision.onlineCandidates().stream()
                            .anyMatch(candidate -> candidate.equalsIgnoreCase(targetServer));
                    if (!stillAvailable) {
                        plugin.cooldowns().clear(player.getUniqueId());
                        player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                                Map.of("reason", "The selected lobby is no longer available.", "player", player.getUsername()), player));
                        return;
                    }

                    boolean sameServer = player.getCurrentServer()
                            .map(current -> current.getServerInfo().getName().equalsIgnoreCase(targetServer))
                            .orElse(false);
                    if (sameServer && !config.commands().reconnectIfSameServer()) {
                        plugin.cooldowns().clear(player.getUniqueId());
                        player.sendMessage(MessageFormatter.render(config.messages().alreadyConnected(),
                                Map.of("server", targetServer, "player", player.getUsername()), player));
                        return;
                    }

                    Optional<RegisteredServer> target = plugin.server().getServer(targetServer);
                    if (target.isEmpty()) {
                        plugin.cooldowns().clear(player.getUniqueId());
                        player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                                Map.of("reason", "The selected server is no longer registered.", "player", player.getUsername()), player));
                        return;
                    }

                    RouteDecision menuDecision = ConnectionWorkflow.withTargetFirst(decision, targetServer, "chat_menu");
                    player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                            Map.of("server", targetServer, "player", player.getUsername()), player));
                    ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), menuDecision, "chat_menu");
                })
                .exceptionally(throwable -> {
                    plugin.cooldowns().clear(player.getUniqueId());
                    player.sendMessage(Component.text("VelocityNavigator could not validate that lobby selection right now.", NamedTextColor.RED));
                    plugin.logger().error("Failed to validate /lobby menu selection for {}", player.getUsername(), throwable);
                    return null;
                });
    }

    private void handleDecision(Player player, Config config, RouteDecision decision, String[] args) {
        // Show Bedrock Cumulus GUI if applicable
        if (plugin.bedrockHandler() != null && plugin.bedrockHandler().isBedrockPlayer(player, config)) {
            if (config.bedrock().useGuiForLobby() && FloodgateIntegration.isAvailable()) {
                BedrockFormService.showLobbySelectionForm(player, plugin, decision);
                return;
            }
        }

        // Show Java Interactive Chat Menu if applicable
        boolean forceMenu = args.length >= 1 && "menu".equalsIgnoreCase(args[0]);
        if (forceMenu || config.routing().useChatMenuForLobby()) {
            JavaMenuService.showLobbyMenu(player, plugin, decision);
            return;
        }

        if (!decision.hasSelection()) {
            // Graceful degradation
            if (config.degradation().enabled()) {
                Config.SelectionMode degradationMode = Config.SelectionMode.fromString(config.degradation().mode());
                List<String> allLobbies = decision.orderedCandidates();
                if (allLobbies == null || allLobbies.isEmpty()) {
                    allLobbies = decision.onlineCandidates();
                }
                if (allLobbies != null && !allLobbies.isEmpty()) {
                    // Try to pick one using degradation mode
                    String degraded = pickDegraded(allLobbies, degradationMode);
                    if (degraded != null) {
                        Optional<RegisteredServer> target = plugin.server().getServer(degraded);
                        if (target.isPresent()) {
                            player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                                    Map.of("server", degraded, "player", player.getUsername()), player));
                            ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), decision, "degradation");
                            return;
                        }
                    }
                }
            }

            plugin.cooldowns().clear(player.getUniqueId());
            String noLobbyMsg = config.messages().noLobbyFound();
            if (config.lobbyFallback() != null && "disconnect".equalsIgnoreCase(config.lobbyFallback().noServerStrategy())) {
                noLobbyMsg = config.lobbyFallback().noServerMessage();
            }
            player.sendMessage(MessageFormatter.render(
                    noLobbyMsg,
                    Map.of(
                            "reason", decision.reason(),
                            "mode", decision.selectionMode().configValue(),
                            "player", player.getUsername()
                    ),
                    player
            ));
            return;
        }

        String targetName = decision.selectedServer();
        boolean sameServer = player.getCurrentServer()
                .map(current -> current.getServerInfo().getName().equalsIgnoreCase(targetName))
                .orElse(false);
        if (sameServer && !config.commands().reconnectIfSameServer()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().alreadyConnected(),
                    Map.of("server", targetName, "player", player.getUsername()), player));
            return;
        }

        Optional<RegisteredServer> target = plugin.server().getServer(targetName);
        if (target.isEmpty()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", "The selected server is no longer registered.", "player", player.getUsername()), player));
            return;
        }

        player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                Map.of("server", targetName, "player", player.getUsername()), player));
        ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), decision, decision.reason());
    }

    /**
     * Picks a server from the degraded candidate list using the configured mode.
     * Modes that require player-count data (LEAST_PLAYERS, POWER_OF_TWO, etc.)
     * fall back to random selection since that data is unavailable in the
     * degradation path. ROUND_ROBIN uses a simple atomic counter.
     */
    private String pickDegraded(List<String> candidates, Config.SelectionMode mode) {
        if (candidates.isEmpty()) {
            return null;
        }
        return switch (mode) {
            case RANDOM -> candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
            case ROUND_ROBIN -> {
                // Use independent degraded atomic counter for round-robin rotation
                long idx = degradedCounter.getAndIncrement();
                yield candidates.get((int) (idx % candidates.size()));
            }
            // Modes that need player-count/telemetry data — fall back to random
            case LEAST_PLAYERS, POWER_OF_TWO, LEAST_CONNECTIONS, WEIGHTED_ROUND_ROBIN, CONSISTENT_HASH, LATENCY ->
                candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
        };
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        Config config = plugin.config();
        if (!requiresPermission(config.commands().permission())) {
            return true;
        }
        return invocation.source().hasPermission(config.commands().permission());
    }

    private boolean hasBypassCooldown(Player player) {
        // Check both new and legacy permission nodes
        return player.hasPermission("velocitynavigator.bypass.cooldown")
                || player.hasPermission("velocitynavigator.bypasscooldown");
    }

    private boolean requiresPermission(String permission) {
        return permission != null
                && !permission.isBlank()
                && !"none".equalsIgnoreCase(permission.trim());
    }
}
