package com.demonz.velocitynavigator;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerAffinityService {

    private final ConcurrentMap<UUID, String> affinityMap = new ConcurrentHashMap<>();
    private final double stickiness;

    public PlayerAffinityService(double stickiness) {
        this.stickiness = Math.max(0.0, Math.min(1.0, stickiness));
    }

    public void setAffinity(UUID playerId, String serverName) {
        affinityMap.put(playerId, serverName);
    }

    public Optional<String> getAffinity(UUID playerId) {
        return Optional.ofNullable(affinityMap.get(playerId));
    }

    public void removeAffinity(UUID playerId) {
        affinityMap.remove(playerId);
    }

    /**
     * Check whether the player should stick to their affinity server.
     * Returns the affinity server name if the player should stick, empty otherwise.
     */
    public Optional<String> shouldStick(UUID playerId, java.util.List<String> candidates) {
        String affinity = affinityMap.get(playerId);
        if (affinity == null) {
            return Optional.empty();
        }
        if (!candidates.contains(affinity)) {
            return Optional.empty();
        }
        if (stickiness >= 1.0) {
            return Optional.of(affinity);
        }
        // FIX-9: Use ThreadLocalRandom instead of Math.random() for thread safety
        if (ThreadLocalRandom.current().nextDouble() < stickiness) {
            return Optional.of(affinity);
        }
        return Optional.empty();
    }

    public Map<UUID, String> getAll() {
        return Map.copyOf(affinityMap);
    }

    public void clear() {
        affinityMap.clear();
    }
}
