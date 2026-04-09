package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
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

    /**
     * Active in-flight ping requests. When multiple callers request a ping for the same server
     * before the first ping completes, they all share the same Future instead of firing
     * duplicate network requests. This prevents ping storms under concurrent /lobby usage.
     */
    private final ConcurrentMap<String, CompletableFuture<ServerStatus>> activePings = new ConcurrentHashMap<>();

    public ServerHealthService(ProxyServer server, Logger logger) {
        this(server, logger, Clock.systemUTC());
    }

    ServerHealthService(ProxyServer server, Logger logger, Clock clock) {
        this.server = server;
        this.logger = logger;
        this.clock = clock;
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

        // Coalesce concurrent pings: if a ping is already in-flight for this server,
        // reuse that Future instead of firing another network request.
        return activePings.computeIfAbsent(serverName, name -> {
            CompletableFuture<ServerStatus> pingFuture = registeredServer.ping()
                    .orTimeout(settings.timeoutMs(), TimeUnit.MILLISECONDS)
                    .thenApply(ignored -> {
                        Instant checkedAt = clock.instant();
                        cache.put(name, true, checkedAt);
                        return new ServerStatus(name, true, true, false, checkedAt, registeredServer.getPlayersConnected().size());
                    })
                    .exceptionally(throwable -> {
                        Instant checkedAt = clock.instant();
                        cache.put(name, false, checkedAt);
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
