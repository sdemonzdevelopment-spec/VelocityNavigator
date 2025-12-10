package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServerPinger {

    private final ProxyServer server;
    private final Config config;
    private final Logger logger;
    private final Map<String, Boolean> onlineStatusCache = new ConcurrentHashMap<>();
    private ScheduledTask scheduledCacheClearer;

    public ServerPinger(Object pluginInstance, ProxyServer server, Config config, Scheduler scheduler, Logger logger) {
        this.server = server;
        this.config = config;
        this.logger = logger;

        if (config.isPingBeforeConnect()) {
            this.scheduledCacheClearer = scheduler.buildTask(pluginInstance, () -> {
                onlineStatusCache.clear();
                logger.debug("Cleared server status cache.");
            })
            .repeat(config.getPingCacheDuration(), TimeUnit.SECONDS)
            .schedule();
        }
    }

    public void shutdown() {
        if (scheduledCacheClearer != null) {
            scheduledCacheClearer.cancel();
        }
    }

    public CompletableFuture<List<String>> getOnlineServers(List<String> serverNames) {
        if (serverNames == null || serverNames.isEmpty()) return CompletableFuture.completedFuture(List.of());
        if (!config.isPingBeforeConnect()) return CompletableFuture.completedFuture(List.copyOf(serverNames));

        List<CompletableFuture<String>> futures = serverNames.stream()
                .map(name -> isOnline(name).thenApply(online -> online ? name : null))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    private CompletableFuture<Boolean> isOnline(String serverName) {
        if (onlineStatusCache.containsKey(serverName)) {
            return CompletableFuture.completedFuture(onlineStatusCache.get(serverName));
        }

        return server.getServer(serverName)
                .map(registeredServer -> registeredServer.ping()
                        // TIMEOUT FIX: Don't wait forever for a laggy server
                        .orTimeout(2500, TimeUnit.MILLISECONDS) 
                        .thenApply(ping -> {
                            onlineStatusCache.put(serverName, true);
                            return true;
                        })
                        .exceptionally(ex -> {
                            // Log only on debug to avoid spam console
                            logger.debug("Server {} is unreachable: {}", serverName, ex.getMessage());
                            onlineStatusCache.put(serverName, false);
                            return false;
                        }))
                .orElseGet(() -> {
                    onlineStatusCache.put(serverName, false);
                    return CompletableFuture.completedFuture(false);
                });
    }
}