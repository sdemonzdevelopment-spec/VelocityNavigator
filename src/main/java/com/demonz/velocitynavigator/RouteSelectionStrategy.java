package com.demonz.velocitynavigator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class RouteSelectionStrategy {

    private final ConcurrentMap<String, AtomicInteger> roundRobinState = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WeightedRoundRobinState> wrrState = new ConcurrentHashMap<>();

    public RouteSelectionStrategy() {
    }

    public Optional<ServerCandidate> select(List<ServerCandidate> candidates, Config.SelectionMode mode, String groupKey) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        return switch (mode) {
            case LEAST_PLAYERS -> candidates.stream()
                    .min(Comparator.comparingInt(ServerCandidate::playerCount).thenComparing(ServerCandidate::name));
            case RANDOM -> Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
            case ROUND_ROBIN -> Optional.of(selectRoundRobin(candidates, groupKey));
            case POWER_OF_TWO -> selectPowerOfTwo(candidates);
            case WEIGHTED_ROUND_ROBIN -> Optional.of(selectWeightedRoundRobin(candidates, groupKey));
            case LEAST_CONNECTIONS -> selectLeastConnections(candidates);
            case CONSISTENT_HASH -> Optional.empty(); // Handled separately by RoutePlanner with player context
        };
    }

    /**
     * Select using consistent hashing with player context.
     * Returns the selected server name (not the candidate itself, since the ring is name-based).
     */
    public Optional<String> selectConsistentHash(ConsistentHashRing ring, String groupKey, String playerId) {
        String server = ring.getServer(groupKey, playerId);
        return Optional.ofNullable(server);
    }

    public void reset() {
        roundRobinState.clear();
        wrrState.clear();
    }

    private ServerCandidate selectRoundRobin(List<ServerCandidate> candidates, String groupKey) {
        List<ServerCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(ServerCandidate::name))
                .toList();
        AtomicInteger cursor = roundRobinState.computeIfAbsent(groupKey == null ? "default" : groupKey, ignored -> new AtomicInteger(0));
        int index = Math.floorMod(cursor.getAndIncrement(), sorted.size());
        return sorted.get(index);
    }

    private Optional<ServerCandidate> selectPowerOfTwo(List<ServerCandidate> candidates) {
        if (candidates.size() <= 2) {
            return candidates.stream()
                    .min(Comparator.comparingInt(ServerCandidate::playerCount).thenComparing(ServerCandidate::name));
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int i = rng.nextInt(candidates.size());
        int j = rng.nextInt(candidates.size());
        ServerCandidate a = candidates.get(i);
        ServerCandidate b = candidates.get(j);
        return Optional.of(a.playerCount() <= b.playerCount() ? a : b);
    }

    private ServerCandidate selectWeightedRoundRobin(List<ServerCandidate> candidates, String groupKey) {
        String key = groupKey == null ? "default" : groupKey;
        WeightedRoundRobinState state = wrrState.computeIfAbsent(key, k -> new WeightedRoundRobinState());

        synchronized (state) {
            // Interleaved WRR algorithm
            int totalWeight = 0;
            for (ServerCandidate c : candidates) {
                totalWeight += c.effectiveWeight();
            }
            if (totalWeight <= 0) {
                return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            }

            // Initialize or resize weight tracking
            state.ensureCapacity(candidates.size());

            ServerCandidate best = null;
            int bestCurrentWeight = Integer.MIN_VALUE;

            for (int idx = 0; idx < candidates.size(); idx++) {
                ServerCandidate c = candidates.get(idx);
                int weight = c.effectiveWeight();
                int currentWeight = state.addWeight(idx, weight);
                if (currentWeight > bestCurrentWeight || (currentWeight == bestCurrentWeight && best != null && c.name().compareTo(best.name()) < 0)) {
                    bestCurrentWeight = currentWeight;
                    best = c;
                }
            }

            if (best != null) {
                int bestIdx = candidates.indexOf(best);
                state.subtractWeight(bestIdx, totalWeight);
            }

            return best != null ? best : candidates.get(0);
        }
    }

    private Optional<ServerCandidate> selectLeastConnections(List<ServerCandidate> candidates) {
        // Use EMA values if available via ServerLoadTracker; otherwise fall back to player counts
        return candidates.stream()
                .min(Comparator.comparingDouble(ServerCandidate::emaLoad).thenComparing(ServerCandidate::name));
    }

    /**
     * State tracker for interleaved weighted round-robin.
     */
    static final class WeightedRoundRobinState {
        private int[] currentWeights;

        void ensureCapacity(int size) {
            if (currentWeights == null || currentWeights.length != size) {
                currentWeights = new int[size];
            }
        }

        int addWeight(int index, int weight) {
            currentWeights[index] += weight;
            return currentWeights[index];
        }

        void subtractWeight(int index, int totalWeight) {
            currentWeights[index] -= totalWeight;
        }
    }
}
