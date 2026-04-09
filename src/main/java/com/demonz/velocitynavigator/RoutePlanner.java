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

public final class RoutePlanner {

    private final RouteSelectionStrategy selectionStrategy;

    public RoutePlanner(RouteSelectionStrategy selectionStrategy) {
        this.selectionStrategy = Objects.requireNonNull(selectionStrategy, "selectionStrategy");
    }

    public RouteDecision plan(String sourceServer, Config config, Map<String, Integer> onlineServers) {
        String normalizedSource = sourceServer == null ? "" : sourceServer.toLowerCase(Locale.ROOT);
        Map<String, Integer> online = onlineServers == null ? Map.of() : toLowerCaseKeys(onlineServers);
        Config.Contextual contextual = config.routing().contextual();

        String requestedGroup = "default";
        List<String> requestedCandidates = config.routing().defaultLobbies();
        boolean contextualMatch = false;
        String reason = "";

        if (contextual.enabled() && !normalizedSource.isBlank()) {
            String mappedGroup = contextual.sources().get(normalizedSource);
            if (mappedGroup != null) {
                requestedGroup = mappedGroup;
                requestedCandidates = contextual.groups().getOrDefault(mappedGroup, List.of());
                contextualMatch = true;
                if (requestedCandidates.isEmpty()) {
                    reason = "Contextual group '" + mappedGroup + "' has no configured lobbies.";
                }
            } else {
                reason = "No contextual mapping exists for '" + normalizedSource + "'.";
            }
        }

        String usedGroup = requestedGroup;
        List<String> configuredCandidates = List.copyOf(requestedCandidates);
        List<String> onlineCandidates = filterOnlineCandidates(requestedCandidates, online);
        boolean fallbackToDefault = false;

        if (contextualMatch && onlineCandidates.isEmpty() && contextual.fallbackToDefault()) {
            configuredCandidates = List.copyOf(config.routing().defaultLobbies());
            onlineCandidates = filterOnlineCandidates(configuredCandidates, online);
            usedGroup = "default";
            fallbackToDefault = true;
            if (reason.isBlank()) {
                reason = "No online servers were available in contextual group '" + requestedGroup + "'.";
            }
        }

        List<String> selectableCandidates = new ArrayList<>(onlineCandidates);
        if (config.routing().cycleWhenPossible() && !normalizedSource.isBlank() && selectableCandidates.size() > 1) {
            selectableCandidates.remove(normalizedSource);
        }

        if (configuredCandidates.isEmpty()) {
            return new RouteDecision(
                    normalizedSource,
                    requestedGroup,
                    usedGroup,
                    configuredCandidates,
                    onlineCandidates,
                    null,
                    fallbackToDefault,
                    reason.isBlank() ? "No configured lobbies were available for group '" + usedGroup + "'." : reason,
                    config.routing().selectionMode()
            );
        }

        if (selectableCandidates.isEmpty()) {
            String finalReason = reason;
            if (finalReason.isBlank()) {
                finalReason = "No online lobbies were available for group '" + usedGroup + "'.";
            }
            return new RouteDecision(
                    normalizedSource,
                    requestedGroup,
                    usedGroup,
                    configuredCandidates,
                    onlineCandidates,
                    null,
                    fallbackToDefault,
                    finalReason,
                    config.routing().selectionMode()
            );
        }

        List<ServerCandidate> candidates = selectableCandidates.stream()
                .map(name -> new ServerCandidate(name, online.getOrDefault(name, 0)))
                .toList();
        Optional<ServerCandidate> selected = selectionStrategy.select(candidates, config.routing().selectionMode(), usedGroup);
        return new RouteDecision(
                normalizedSource,
                requestedGroup,
                usedGroup,
                configuredCandidates,
                onlineCandidates,
                selected.map(ServerCandidate::name).orElse(null),
                fallbackToDefault,
                fallbackToDefault ? reason : "",
                config.routing().selectionMode()
        );
    }

    public Set<String> inspectionTargets(String sourceServer, Config config) {
        Set<String> targets = new LinkedHashSet<>(config.routing().defaultLobbies());
        Config.Contextual contextual = config.routing().contextual();
        String normalized = sourceServer == null ? "" : sourceServer.toLowerCase(Locale.ROOT);
        if (contextual.enabled() && !normalized.isBlank()) {
            String group = contextual.sources().get(normalized);
            if (group != null) {
                targets.addAll(contextual.groups().getOrDefault(group, List.of()));
            }
        }
        return targets;
    }

    private List<String> filterOnlineCandidates(List<String> configuredCandidates, Map<String, Integer> onlineServers) {
        List<String> online = new ArrayList<>();
        for (String candidate : configuredCandidates) {
            if (onlineServers.containsKey(candidate.toLowerCase(Locale.ROOT))) {
                online.add(candidate);
            }
        }
        return List.copyOf(online);
    }

    private Map<String, Integer> toLowerCaseKeys(Map<String, Integer> original) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : original.entrySet()) {
            normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return normalized;
    }
}

