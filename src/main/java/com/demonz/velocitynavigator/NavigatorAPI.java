package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Public API for VelocityNavigator.
 * <p>
 * Other Velocity plugins can access this via {@link NavigatorAPIProvider#get()}.
 * <p>
 * Example:
 * <pre>{@code
 * NavigatorAPI api = NavigatorAPIProvider.get();
 * if (api != null) {
 *     api.previewRoute(player).thenAccept(decision -> {
 *         System.out.println("Best lobby: " + decision.selectedServer());
 *     });
 * }
 * }</pre>
 */
public interface NavigatorAPI {

    /**
     * Preview the routing decision for a player without executing it.
     * This runs the full routing pipeline (health checks, selection mode, contextual groups)
     * and returns the decision the plugin would make, but does NOT move the player.
     *
     * @param player the player to preview routing for
     * @return a future containing the route decision
     */
    CompletableFuture<RouteDecision> previewRoute(Player player);

    /**
     * Inspect a server's current health status.
     * This returns the server's online/offline state, player count, and cache information.
     *
     * @param serverName the registered server name (case-insensitive)
     * @return a future containing the server status
     */
    CompletableFuture<ServerHealthService.ServerStatus> inspectServer(String serverName);

    /**
     * Get the current routing configuration.
     *
     * @return the active routing config
     */
    Config.Routing getRoutingConfig();

    /**
     * Get the currently active selection mode.
     *
     * @return the selection mode (LEAST_PLAYERS, ROUND_ROBIN, or RANDOM)
     */
    Config.SelectionMode getSelectionMode();
}
