package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class LobbyCommand implements SimpleCommand {

    private final VelocityNavigator plugin;

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

        if (!hasBypassCooldown(player)) {
            OptionalLong secondsRemaining = plugin.cooldowns().secondsRemaining(player.getUniqueId());
            if (secondsRemaining.isPresent()) {
                player.sendMessage(MessageFormatter.render(config.messages().cooldown(), Map.of("time", String.valueOf(secondsRemaining.getAsLong()))));
                return;
            }
        }

        // Apply cooldown pre-execution to block macro spam before the async route resolves.
        // If routing fails, the cooldown is cleared so the player is not penalized.
        plugin.cooldowns().apply(player.getUniqueId(), config.commands().cooldownSeconds());

        plugin.previewRoute(player)
                .thenAccept(decision -> handleDecision(player, config, decision))
                .exceptionally(throwable -> {
                    plugin.cooldowns().clear(player.getUniqueId());
                    player.sendMessage(Component.text("VelocityNavigator could not resolve a lobby right now.", NamedTextColor.RED));
                    plugin.logger().error("Failed to resolve /lobby for {}", player.getUsername(), throwable);
                    return null;
                });
    }

    private void handleDecision(Player player, Config config, RouteDecision decision) {
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
                                    Map.of("server", degraded, "player", player.getUsername())));
                            connectWithRetry(player, config, target.get(), decision, 0, new HashSet<>());
                            return;
                        }
                    }
                }
            }

            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(
                    config.messages().noLobbyFound(),
                    Map.of(
                            "reason", decision.reason(),
                            "mode", decision.selectionMode().configValue(),
                            "player", player.getUsername()
                    )
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
                    Map.of("server", targetName, "player", player.getUsername())));
            return;
        }

        Optional<RegisteredServer> target = plugin.server().getServer(targetName);
        if (target.isEmpty()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", "The selected server is no longer registered.", "player", player.getUsername())));
            return;
        }

        player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                Map.of("server", targetName, "player", player.getUsername())));
        connectWithRetry(player, config, target.get(), decision, 0, new HashSet<>());
    }

    /**
     * Chained async retry: attempts connection, and on failure recursively tries
     * the next candidate from the ordered fallback list. Respects maxRetries config.
     *
     * @param player       the player to connect
     * @param config       current routing configuration
     * @param target       the server to attempt connection to
     * @param decision     the original routing decision (contains ordered candidates)
     * @param attempt      current attempt number (0 = initial, 1+ = retry)
     * @param triedServers accumulator of all previously-tried server names (FIX-8)
     */
    private void connectWithRetry(Player player, Config config, RegisteredServer target,
                                  RouteDecision decision, int attempt, Set<String> triedServers) {
        int maxRetries = config.routing().maxRetries();
        // FIX-8: Track this server as tried
        triedServers.add(target.getServerInfo().getName().toLowerCase());

        player.createConnectionRequest(target).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                // Record routing stats and affinity
                plugin.routingStats().recordConnection(target.getServerInfo().getName());
                // FIX-2: Record connection rate for load balancing
                if (plugin.rateTracker() != null) {
                    plugin.rateTracker().recordConnection(target.getServerInfo().getName());
                }
                if (plugin.affinityService() != null) {
                    plugin.affinityService().setAffinity(player.getUniqueId(), target.getServerInfo().getName());
                }
                return;
            }

            // If we have retries remaining, try the next candidate
            if (attempt < maxRetries) {
                String nextServer = pickNextCandidate(decision, triedServers);
                if (nextServer != null) {
                    Optional<RegisteredServer> nextTarget = plugin.server().getServer(nextServer);
                    if (nextTarget.isPresent()) {
                        player.sendMessage(MessageFormatter.render(config.messages().retrying(),
                                Map.of("attempt", String.valueOf(attempt + 1),
                                       "max", String.valueOf(maxRetries),
                                       "player", player.getUsername(),
                                       "server", nextServer)));
                        connectWithRetry(player, config, nextTarget.get(), decision, attempt + 1, triedServers);
                        return;
                    }
                }
            }

            // All retries exhausted or no more candidates
            plugin.cooldowns().clear(player.getUniqueId());
            if (attempt == 0) {
                // Initial connection failed, no retries attempted
                Component reason = result.getReasonComponent()
                        .orElse(Component.text("Unknown error", NamedTextColor.RED));
                player.sendMessage(Component.text("Failed to connect: ", NamedTextColor.RED).append(reason));
            } else {
                player.sendMessage(Component.text("Failed to connect after " + (attempt + 1) + " attempt(s).", NamedTextColor.RED));
            }
        });
    }

    /**
     * FIX-8: Picks the next candidate server from the ordered fallback list,
     * skipping ALL servers that have been tried across all retry attempts.
     */
    private String pickNextCandidate(RouteDecision decision, Set<String> triedServers) {
        List<String> candidates = decision.orderedCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        for (String candidate : candidates) {
            if (!triedServers.contains(candidate.toLowerCase())) {
                return candidate;
            }
        }

        return null; // No more candidates available
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
                // Use routing stats counter for simple round-robin rotation
                long idx = plugin.routingStats().totalConnections();
                yield candidates.get((int) (idx % candidates.size()));
            }
            // Modes that need player-count data — fall back to random
            case LEAST_PLAYERS, POWER_OF_TWO, LEAST_CONNECTIONS, WEIGHTED_ROUND_ROBIN, CONSISTENT_HASH ->
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
