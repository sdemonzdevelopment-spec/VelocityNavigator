package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServerPinger {

    private final ProxyServer server;
    private final Config config;
    private final Map<String, Boolean> onlineStatusCache = new ConcurrentHashMap<>();

    // FIX: Accept the main plugin instance in the constructor
    public ServerPinger(Object pluginInstance, ProxyServer server, Config config, Scheduler scheduler) {
        this.server = server;
        this.config = config;

        if (config.isPingBeforeConnect()) {
            // FIX: Use the direct plugin instance to build the task
            scheduler.buildTask(pluginInstance, onlineStatusCache::clear)
                    .delay(config.getPingCacheDuration(), TimeUnit.SECONDS)
                    .repeat(config.getPingCacheDuration(), TimeUnit.SECONDS)
                    .schedule();
        }
    }

    public CompletableFuture<List<String>> getOnlineServers(List<String> serverNames) {
        if (!config.isPingBeforeConnect() || serverNames == null || serverNames.isEmpty()) {
            return CompletableFuture.completedFuture(serverNames);
        }

        List<CompletableFuture<String>> futures = serverNames.stream()
            .map(serverName -> isOnline(serverName).thenApply(online -> online ? serverName : null))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(name -> name != null)
                        .collect(Collectors.toList()));
    }

    private CompletableFuture<Boolean> isOnline(String serverName) {
        if (onlineStatusCache.containsKey(serverName)) {
            return CompletableFuture.completedFuture(onlineStatusCache.get(serverName));
        }

        return server.getServer(serverName)
                .map(RegisteredServer::ping)
                .map(pingFuture -> pingFuture.thenApply(serverPing -> {
                    onlineStatusCache.put(serverName, true);
                    return true;
                }).exceptionally(throwable -> {
                    onlineStatusCache.put(serverName, false);
                    return false;
                }))
                .orElse(CompletableFuture.completedFuture(false));
    }
}
