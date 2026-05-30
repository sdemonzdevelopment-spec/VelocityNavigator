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

import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.Map;
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
     * @return the selection mode
     */
    Config.SelectionMode getSelectionMode();

    /**
     * Get the routing distribution from the current stats window.
     *
     * @return map of server name to connection count
     */
    Map<String, Long> getRoutingDistribution();

    /**
     * Get health check latencies (placeholder for future implementation).
     *
     * @return map of server name to latency in ms
     */
    Map<String, Long> getHealthCheckLatencies();

    /**
     * Get circuit breaker statuses for all tracked servers.
     *
     * @return map of server name to circuit breaker state
     */
    Map<String, CircuitBreaker.State> getCircuitBreakerStatuses();
}
