package com.demonz.velocitynavigator;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RouteSelectionStrategy {

    private final Random random;
    private final ConcurrentMap<String, AtomicInteger> roundRobinState = new ConcurrentHashMap<>();

    public RouteSelectionStrategy(Random random) {
        this.random = random;
    }

    public Optional<ServerCandidate> select(List<ServerCandidate> candidates, Config.SelectionMode mode, String groupKey) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        return switch (mode) {
            case LEAST_PLAYERS -> candidates.stream()
                    .min(Comparator.comparingInt(ServerCandidate::playerCount).thenComparing(ServerCandidate::name));
            case RANDOM -> Optional.of(candidates.get(random.nextInt(candidates.size())));
            case ROUND_ROBIN -> Optional.of(selectRoundRobin(candidates, groupKey));
        };
    }

    public void reset() {
        roundRobinState.clear();
    }

    private ServerCandidate selectRoundRobin(List<ServerCandidate> candidates, String groupKey) {
        List<ServerCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(ServerCandidate::name))
                .toList();
        AtomicInteger cursor = roundRobinState.computeIfAbsent(groupKey == null ? "default" : groupKey, ignored -> new AtomicInteger(0));
        int index = Math.floorMod(cursor.getAndIncrement(), sorted.size());
        return sorted.get(index);
    }
}

