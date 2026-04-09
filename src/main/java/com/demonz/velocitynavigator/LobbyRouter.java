package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class LobbyRouter {

    private final ServerHealthService healthService;
    private final RoutePlanner routePlanner;

    public LobbyRouter(ServerHealthService healthService, RoutePlanner routePlanner) {
        this.healthService = healthService;
        this.routePlanner = routePlanner;
    }

    public CompletableFuture<RouteDecision> preview(Player player, Config config) {
        String sourceServer = player.getCurrentServer()
                .map(current -> current.getServerInfo().getName())
                .orElse("");
        return preview(sourceServer, config);
    }

    public CompletableFuture<RouteDecision> preview(String sourceServer, Config config) {
        Set<String> targets = routePlanner.inspectionTargets(sourceServer, config);
        return healthService.inspectServers(targets, config.healthChecks())
                .thenApply(statuses -> routePlanner.plan(sourceServer, config, onlinePlayers(statuses)));
    }

    private Map<String, Integer> onlinePlayers(Map<String, ServerHealthService.ServerStatus> statuses) {
        Map<String, Integer> online = new LinkedHashMap<>();
        for (Map.Entry<String, ServerHealthService.ServerStatus> entry : statuses.entrySet()) {
            if (entry.getValue().exists() && entry.getValue().online()) {
                online.put(entry.getKey(), entry.getValue().playersConnected());
            }
        }
        return online;
    }
}
