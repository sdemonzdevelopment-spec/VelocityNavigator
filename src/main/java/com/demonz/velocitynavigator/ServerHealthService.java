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

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class ServerHealthService {

    private final ProxyServer server;
    private final Logger logger;
    private final HealthCheckCache cache = new HealthCheckCache();
    private final Clock clock;
    private CircuitBreaker circuitBreaker;
    private ServerLoadTracker loadTracker;

    private final ConcurrentMap<String, CompletableFuture<ServerStatus>> activePings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> latencies = new ConcurrentHashMap<>();

    public Map<String, Long> getLatencies() {
        return java.util.Collections.unmodifiableMap(latencies);
    }

    public ServerHealthService(ProxyServer server, Logger logger) {
        this(server, logger, Clock.systemUTC());
    }

    ServerHealthService(ProxyServer server, Logger logger, Clock clock) {
        this.server = server;
        this.logger = logger;
        this.clock = clock;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void setLoadTracker(ServerLoadTracker loadTracker) {
        this.loadTracker = loadTracker;
    }

    /**
     * Returns cached player counts from the cache without triggering async operations.
     * Iterates over entries in the cache, for each fresh entry, checks if the server
     * exists and is online from the proxy's registered servers, and builds a map of
     * server name → player count.
     */
    public Map<String, Integer> getCachedOnlineServers() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, HealthCheckCache.Entry> entry : cache.entries().entrySet()) {
            String serverName = entry.getKey();
            HealthCheckCache.Entry cached = entry.getValue();
            if (cached == null || !cached.online()) {
                continue;
            }
            // Verify the server still exists and is registered
            Optional<RegisteredServer> registered = server.getServer(serverName);
            if (registered.isEmpty()) {
                continue;
            }
            int playerCount = registered.get().getPlayersConnected().size();
            result.put(serverName, playerCount);
            // Keep EMA load estimates fresh when cached health data is reused.
            if (loadTracker != null) {
                loadTracker.update(serverName, playerCount);
            }
        }
        return result;
    }

    public Map<String, Integer> getRegisteredOnlineServers(Collection<String> serverNames) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (serverNames == null) {
            return result;
        }
        for (String serverName : serverNames) {
            if (serverName == null || serverName.isBlank()) {
                continue;
            }
            Optional<RegisteredServer> registered = server.getServer(serverName);
            if (registered.isEmpty()) {
                continue;
            }
            int playerCount = registered.get().getPlayersConnected().size();
            String normalized = serverName.toLowerCase(Locale.ROOT);
            result.put(normalized, playerCount);
            if (loadTracker != null) {
                loadTracker.update(normalized, playerCount);
            }
        }
        return result;
    }

    public CompletableFuture<Map<String, ServerStatus>> inspectServers(Collection<String> serverNames, Config.HealthChecks settings) {
        Map<String, CompletableFuture<ServerStatus>> futures = new LinkedHashMap<>();
        for (String serverName : serverNames) {
            if (serverName == null || serverName.isBlank() || futures.containsKey(serverName)) {
                continue;
            }
            futures.put(serverName, inspectServer(serverName, settings));
        }
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    Map<String, ServerStatus> results = new LinkedHashMap<>();
                    for (Map.Entry<String, CompletableFuture<ServerStatus>> entry : futures.entrySet()) {
                        results.put(entry.getKey(), entry.getValue().join());
                    }
                    return results;
                });
    }

    public CompletableFuture<ServerStatus> inspectServer(String serverName, Config.HealthChecks settings) {
        Optional<RegisteredServer> optionalServer = server.getServer(serverName);
        if (optionalServer.isEmpty()) {
            return CompletableFuture.completedFuture(new ServerStatus(serverName, false, false, false, null, 0));
        }

        RegisteredServer registeredServer = optionalServer.get();
        int players = registeredServer.getPlayersConnected().size();
        Instant now = clock.instant();
        if (!settings.enabled()) {
            return CompletableFuture.completedFuture(new ServerStatus(serverName, true, true, false, now, players));
        }

        HealthCheckCache.Entry cachedEntry = cache.getIfFresh(serverName, now, Duration.ofSeconds(settings.cacheSeconds()));
        if (cachedEntry != null) {
            return CompletableFuture.completedFuture(new ServerStatus(serverName, true, cachedEntry.online(), true, cachedEntry.checkedAt(), players));
        }

        long startTime = System.currentTimeMillis();

        // Coalesce concurrent pings: if a ping is already in-flight for this server,
        // reuse that Future instead of firing another network request.
        return activePings.computeIfAbsent(serverName, name -> {
            CompletableFuture<ServerStatus> pingFuture = registeredServer.ping()
                    .orTimeout(settings.timeoutMs(), TimeUnit.MILLISECONDS)
                    .thenApply(ignored -> {
                        long latency = System.currentTimeMillis() - startTime;
                        latencies.put(name.toLowerCase(java.util.Locale.ROOT), latency);
                        Instant checkedAt = clock.instant();
                        cache.put(name, true, checkedAt);
                        int currentPlayers = registeredServer.getPlayersConnected().size();
                        // Record success on circuit breaker
                        if (circuitBreaker != null) {
                            circuitBreaker.recordSuccess(name);
                        }
                        // Update EMA load tracker on successful health check.
                        if (loadTracker != null) {
                            loadTracker.update(name, currentPlayers);
                        }
                        return new ServerStatus(name, true, true, false, checkedAt, currentPlayers);
                    })
                    .exceptionally(throwable -> {
                        latencies.remove(name.toLowerCase(java.util.Locale.ROOT));
                        Instant checkedAt = clock.instant();
                        cache.put(name, false, checkedAt);
                        // Record failure on circuit breaker
                        if (circuitBreaker != null) {
                            circuitBreaker.recordFailure(name);
                        }
                        // Update EMA load tracker with 0 for offline servers.
                        if (loadTracker != null) {
                            loadTracker.update(name, 0);
                        }
                        logger.debug("VelocityNavigator health check marked {} offline: {}", name, throwable.getMessage());
                        return new ServerStatus(name, true, false, false, checkedAt, registeredServer.getPlayersConnected().size());
                    });

            // Remove from active pings map once the future completes, so the next
            // request after cache expiry can fire a fresh ping.
            pingFuture.whenComplete((result, error) -> activePings.remove(name));
            return pingFuture;
        });
    }

    public void clearCache() {
        cache.clear();
        activePings.clear();
    }

    /**
     * Delegating method to purge expired cache entries.
     */
    public void purgeExpiredCache(Duration ttl) {
        cache.purgeExpired(ttl);
    }

    public record ServerStatus(
            String serverName,
            boolean exists,
            boolean online,
            boolean cached,
            Instant checkedAt,
            int playersConnected
    ) {
    }
}
