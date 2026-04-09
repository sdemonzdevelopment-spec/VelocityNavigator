package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

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

        if (!player.hasPermission("velocitynavigator.bypasscooldown")) {
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
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(
                    config.messages().noLobbyFound(),
                    Map.of(
                            "reason", decision.reason(),
                            "mode", decision.selectionMode().configValue()
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
            player.sendMessage(MessageFormatter.render(config.messages().alreadyConnected(), Map.of("server", targetName)));
            return;
        }

        Optional<RegisteredServer> target = plugin.server().getServer(targetName);
        if (target.isEmpty()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(), Map.of("reason", "The selected server is no longer registered.")));
            return;
        }

        player.sendMessage(MessageFormatter.render(config.messages().connecting(), Map.of("server", targetName)));
        player.createConnectionRequest(target.get()).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                return;
            }
            plugin.cooldowns().clear(player.getUniqueId());
            Component reason = result.getReasonComponent().orElse(Component.text("Unknown error", NamedTextColor.RED));
            player.sendMessage(Component.text("Failed to connect: ", NamedTextColor.RED).append(reason));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        Config config = plugin.config();
        if (!requiresPermission(config.commands().permission())) {
            return true;
        }
        return invocation.source().hasPermission(config.commands().permission());
    }

    private boolean requiresPermission(String permission) {
        return permission != null
                && !permission.isBlank()
                && !"none".equalsIgnoreCase(permission.trim());
    }
}
