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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoutePlanner {

    private final RouteSelectionStrategy selectionStrategy;
    private volatile DrainService drainService;
    private volatile CircuitBreaker circuitBreaker;
    private volatile ServerLoadTracker loadTracker;
    private volatile ConsistentHashRing hashRing;
    private volatile PlayerAffinityService affinityService;
    private volatile ConnectionRateTracker rateTracker;
    private volatile ServerHealthService healthService;

    public RoutePlanner(RouteSelectionStrategy selectionStrategy) {
        this.selectionStrategy = Objects.requireNonNull(selectionStrategy, "selectionStrategy");
    }

    public void setHealthService(ServerHealthService healthService) {
        this.healthService = healthService;
    }

    public void setDrainService(DrainService drainService) {
        this.drainService = drainService;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void setLoadTracker(ServerLoadTracker loadTracker) {
        this.loadTracker = loadTracker;
    }

    public void setHashRing(ConsistentHashRing hashRing) {
        this.hashRing = hashRing;
    }

    public void setAffinityService(PlayerAffinityService affinityService) {
        this.affinityService = affinityService;
    }

    public void setRateTracker(ConnectionRateTracker rateTracker) {
        this.rateTracker = rateTracker;
    }

    /**
     * Plan a route without a player identity.
     * <p>
     * When {@code playerId} is null, player-dependent features are skipped:
     * <ul>
     *   <li>Player affinity (sticky sessions) is not evaluated</li>
     *   <li>Consistent hash selection falls back to the next selection strategy</li>
     * </ul>
     * This overload passes {@code null} as the playerId. Use
     * {@link #plan(String, Config, Map, UUID)} when a player context is available.
     *
     * @param sourceServer  the server the player is currently on (empty string if none)
     * @param config        the active routing configuration
     * @param onlineServers map of server name → current player count for online servers
     * @return the routing decision
     */
    public RouteDecision plan(String sourceServer, Config config, Map<String, Integer> onlineServers) {
        return plan(sourceServer, config, onlineServers, null);
    }

    /**
     * Plan a route with full player context.
     * <p>
     * When {@code playerId} is null, player-dependent features are skipped:
     * <ul>
     *   <li>Player affinity (sticky sessions) is not evaluated</li>
     *   <li>Consistent hash selection falls back to the next selection strategy</li>
     * </ul>
     * Pass a non-null playerId when a player context is available to enable
     * affinity and consistent hash routing.
     *
     * @param sourceServer  the server the player is currently on (empty string if none)
     * @param config        the active routing configuration
     * @param onlineServers map of server name → current player count for online servers
     * @param playerId      the player's unique ID, or null if no player context is available
     * @return the routing decision
     */
    public RouteDecision plan(String sourceServer, Config config, Map<String, Integer> onlineServers, UUID playerId) {
        String normalizedSource = sourceServer == null ? "" : sourceServer.toLowerCase(Locale.ROOT);
        Map<String, Integer> online = onlineServers == null ? Map.of() : toLowerCaseKeys(onlineServers);
        Config.Contextual contextual = config.routing().contextual();

        String requestedGroup = "default";
        List<Config.LobbyEntry> requestedEntries = config.routing().defaultLobbies();
        Config.SelectionMode groupMode = null;
        boolean contextualMatch = false;
        String reason = "";

        if (contextual.enabled() && !normalizedSource.isBlank()) {
            String mappedGroup = contextual.sources().get(normalizedSource);
            if (mappedGroup != null) {
                Config.GroupConfig groupConfig = contextual.groups().get(mappedGroup);
                requestedGroup = mappedGroup;
                if (groupConfig != null) {
                    requestedEntries = groupConfig.servers();
                    groupMode = groupConfig.mode();
                } else {
                    requestedEntries = List.of();
                }
                contextualMatch = true;
                if (requestedEntries.isEmpty()) {
                    reason = "Contextual group '" + mappedGroup + "' has no configured lobbies.";
                }
            } else {
                reason = "No contextual mapping exists for '" + normalizedSource + "'.";
            }
        }

        String usedGroup = requestedGroup;
        List<Config.LobbyEntry> configuredEntries = List.copyOf(requestedEntries);
        Config.SelectionMode effectiveMode = groupMode != null ? groupMode : config.routing().selectionMode();
        List<String> onlineCandidates = filterOnlineCandidates(requestedEntries, online);
        boolean fallbackToDefault = false;

        if (contextualMatch && onlineCandidates.isEmpty() && contextual.fallbackToDefault()) {
            // Try fallback chain first
            List<String> chain = contextual.fallbackChain().getOrDefault(requestedGroup, List.of());
            for (String fallbackGroup : chain) {
                Config.GroupConfig fallbackConfig = contextual.groups().get(fallbackGroup);
                if (fallbackConfig != null) {
                    List<String> fallbackOnline = filterOnlineCandidates(fallbackConfig.servers(), online);
                    if (!fallbackOnline.isEmpty()) {
                        configuredEntries = List.copyOf(fallbackConfig.servers());
                        onlineCandidates = fallbackOnline;
                        usedGroup = fallbackGroup;
                        effectiveMode = fallbackConfig.mode() != null ? fallbackConfig.mode() : config.routing().selectionMode();
                        fallbackToDefault = true;
                        reason = "No online servers in contextual group '" + requestedGroup + "'; fell back to '" + fallbackGroup + "'.";
                        break;
                    }
                }
            }

            // If fallback chain didn't help, use default lobbies
            if (onlineCandidates.isEmpty()) {
                configuredEntries = List.copyOf(config.routing().defaultLobbies());
                onlineCandidates = filterOnlineCandidates(configuredEntries, online);
                usedGroup = "default";
                effectiveMode = config.routing().selectionMode();
                fallbackToDefault = true;
                if (reason.isBlank()) {
                    reason = "No online servers were available in contextual group '" + requestedGroup + "'.";
                }
            }
        }

        List<String> selectableCandidates = new ArrayList<>(onlineCandidates);
        if (config.routing().cycleWhenPossible() && !normalizedSource.isBlank() && selectableCandidates.size() > 1) {
            selectableCandidates.remove(normalizedSource);
        }

        if (configuredEntries.isEmpty()) {
            Optional<String> fallbackServer = selectableFallbackServer(config, online);
            if (fallbackServer.isPresent()) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        fallbackServer.get(),
                        fallbackToDefault,
                        "Fell back to fallback server: " + fallbackServer.get(),
                        effectiveMode,
                        selectableCandidates
                );
            }
            return new RouteDecision(
                    normalizedSource,
                    requestedGroup,
                    usedGroup,
                    lobbyEntryNames(configuredEntries),
                    onlineCandidates,
                    null,
                    fallbackToDefault,
                    reason.isBlank() ? (config.lobbyFallback() != null ? config.lobbyFallback().noServerMessage() : "No configured lobbies were available for group '" + usedGroup + "'.") : reason,
                    effectiveMode
            );
        }

        if (selectableCandidates.isEmpty()) {
            Optional<String> fallbackServer = selectableFallbackServer(config, online);
            if (fallbackServer.isPresent()) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        fallbackServer.get(),
                        fallbackToDefault,
                        "Fell back to fallback server: " + fallbackServer.get(),
                        effectiveMode,
                        selectableCandidates
                );
            }
            String finalReason = reason;
            if (finalReason.isBlank()) {
                finalReason = config.lobbyFallback() != null ? config.lobbyFallback().noServerMessage() : "No online lobbies were available for group '" + usedGroup + "'.";
            }
            return new RouteDecision(
                    normalizedSource,
                    requestedGroup,
                    usedGroup,
                    lobbyEntryNames(configuredEntries),
                    onlineCandidates,
                    null,
                    fallbackToDefault,
                    finalReason,
                    effectiveMode
            );
        }

        // Player affinity check
        if (playerId != null && affinityService != null && effectiveMode != Config.SelectionMode.CONSISTENT_HASH) {
            Optional<String> stickServer = affinityService.shouldStick(playerId, selectableCandidates);
            if (stickServer.isPresent()) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        stickServer.get(),
                        fallbackToDefault,
                        "affinity",
                        effectiveMode,
                        selectableCandidates
                );
            }
        }

        // Consistent hash path
        if (effectiveMode == Config.SelectionMode.CONSISTENT_HASH && playerId != null && hashRing != null) {
            hashRing.updateRing(usedGroup, selectableCandidates);
            Optional<String> selected = selectionStrategy.selectConsistentHash(hashRing, usedGroup, playerId.toString());
            if (selected.isPresent() && selectableCandidates.contains(selected.get())) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        selected.get(),
                        fallbackToDefault,
                        "consistent_hash",
                        effectiveMode,
                        hashRing.getServerOrder(usedGroup, playerId.toString())
                );
            }
        }

        List<Config.LobbyEntry> finalEntries = configuredEntries;
        List<ServerCandidate> candidates = selectableCandidates.stream()
                .map(name -> buildCandidate(name, online.getOrDefault(name, 0), finalEntries))
                .toList();
        Config.SelectionMode selectMode = effectiveMode == Config.SelectionMode.CONSISTENT_HASH
                ? Config.SelectionMode.LEAST_PLAYERS
                : effectiveMode;
        Optional<ServerCandidate> selected = selectionStrategy.select(candidates, selectMode, usedGroup);
        String finalReason = fallbackToDefault ? reason : selectMode.configValue();
        if (effectiveMode == Config.SelectionMode.CONSISTENT_HASH) {
            finalReason = "Consistent hash selection was unavailable or failed; fell back to LEAST_PLAYERS.";
        }
        return new RouteDecision(
                normalizedSource,
                requestedGroup,
                usedGroup,
                lobbyEntryNames(configuredEntries),
                onlineCandidates,
                selected.map(ServerCandidate::name).orElse(null),
                fallbackToDefault,
                finalReason,
                selectMode,
                selectableCandidates
        );
    }

    public Set<String> inspectionTargets(String sourceServer, Config config) {
        Set<String> targets = new LinkedHashSet<>();
        for (Config.LobbyEntry entry : config.routing().defaultLobbies()) {
            targets.add(entry.server());
        }
        if (config.lobbyFallback() != null
                && "fallback_server".equalsIgnoreCase(config.lobbyFallback().noServerStrategy())
                && !config.lobbyFallback().fallbackServer().isBlank()) {
            targets.add(config.lobbyFallback().fallbackServer());
        }
        Config.Contextual contextual = config.routing().contextual();
        String normalized = sourceServer == null ? "" : sourceServer.toLowerCase(Locale.ROOT);
        if (contextual.enabled() && !normalized.isBlank()) {
            String group = contextual.sources().get(normalized);
            if (group != null) {
                Config.GroupConfig groupConfig = contextual.groups().get(group);
                if (groupConfig != null) {
                    for (Config.LobbyEntry entry : groupConfig.servers()) {
                        targets.add(entry.server());
                    }
                }
            }
        }
        return targets;
    }

    private List<String> filterOnlineCandidates(List<Config.LobbyEntry> configuredEntries, Map<String, Integer> onlineServers) {
        List<String> online = new ArrayList<>();
        for (Config.LobbyEntry entry : configuredEntries) {
            String name = entry.server().toLowerCase(Locale.ROOT);
            Integer count = onlineServers.get(name);
            if (count == null) {
                continue;
            }
            // Check if server is drained
            if (drainService != null && drainService.isDrained(name)) {
                continue;
            }
            // Check circuit breaker
            if (circuitBreaker != null && !circuitBreaker.isAvailable(name)) {
                continue;
            }
            // Check max-player cap
            if (entry.isFull(count)) {
                continue;
            }
            online.add(entry.server());
        }
        return List.copyOf(online);
    }

    private ServerCandidate buildCandidate(String name, int playerCount, List<Config.LobbyEntry> entries) {
        int weight = Config.LobbyEntry.DEFAULT_WEIGHT;
        for (Config.LobbyEntry entry : entries) {
            if (entry.server().equalsIgnoreCase(name)) {
                weight = entry.effectiveWeight();
                break;
            }
        }
        double emaLoad = playerCount;
        if (loadTracker != null) {
            emaLoad = loadTracker.getEma(name);
        }
        double rateCost = 0.0;
        if (rateTracker != null) {
            rateCost = rateTracker.getRatePerSecond(name);
        }
        // Incorporate rate into emaLoad for LEAST_CONNECTIONS
        double combinedLoad = emaLoad + rateCost;
        long latency = -1L;
        if (healthService != null) {
            Long tracked = healthService.getLatencies().get(name.toLowerCase(Locale.ROOT));
            if (tracked != null) {
                latency = tracked;
            }
        }
        return new ServerCandidate(name, playerCount, weight, combinedLoad, latency);
    }

    private Optional<String> selectableFallbackServer(Config config, Map<String, Integer> onlineServers) {
        if (config.lobbyFallback() == null
                || !"fallback_server".equalsIgnoreCase(config.lobbyFallback().noServerStrategy())
                || config.lobbyFallback().fallbackServer().isBlank()) {
            return Optional.empty();
        }
        String fallbackServer = config.lobbyFallback().fallbackServer();
        String normalized = fallbackServer.toLowerCase(Locale.ROOT);
        if (!onlineServers.containsKey(normalized)) {
            return Optional.empty();
        }
        if (drainService != null && drainService.isDrained(normalized)) {
            return Optional.empty();
        }
        if (circuitBreaker != null && !circuitBreaker.isAvailable(normalized)) {
            return Optional.empty();
        }
        return Optional.of(fallbackServer);
    }

    private List<String> lobbyEntryNames(List<Config.LobbyEntry> entries) {
        List<String> names = new ArrayList<>();
        for (Config.LobbyEntry entry : entries) {
            names.add(entry.server());
        }
        return List.copyOf(names);
    }

    private Map<String, Integer> toLowerCaseKeys(Map<String, Integer> original) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : original.entrySet()) {
            normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return normalized;
    }
}
