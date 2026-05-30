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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerAffinityService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final ConcurrentMap<UUID, AffinityEntry> affinityMap = new ConcurrentHashMap<>();
    private final double stickiness;
    private final Duration ttl;

    public PlayerAffinityService(double stickiness) {
        this(stickiness, DEFAULT_TTL);
    }

    PlayerAffinityService(double stickiness, Duration ttl) {
        this.stickiness = Math.max(0.0, Math.min(1.0, stickiness));
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
    }

    public void setAffinity(UUID playerId, String serverName) {
        if (playerId == null || serverName == null || serverName.isBlank()) {
            return;
        }
        affinityMap.put(playerId, new AffinityEntry(serverName, Instant.now()));
    }

    public Optional<String> getAffinity(UUID playerId) {
        AffinityEntry entry = affinityMap.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry, Instant.now())) {
            affinityMap.remove(playerId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.serverName());
    }

    public void removeAffinity(UUID playerId) {
        affinityMap.remove(playerId);
    }

    /**
     * Check whether the player should stick to their affinity server.
     * Returns the affinity server name if the player should stick, empty otherwise.
     */
    public Optional<String> shouldStick(UUID playerId, java.util.List<String> candidates) {
        AffinityEntry entry = affinityMap.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry, Instant.now())) {
            affinityMap.remove(playerId, entry);
            return Optional.empty();
        }
        String affinity = entry.serverName();
        if (!candidates.contains(affinity)) {
            return Optional.empty();
        }
        if (stickiness >= 1.0) {
            return Optional.of(affinity);
        }
        if (ThreadLocalRandom.current().nextDouble() < stickiness) {
            return Optional.of(affinity);
        }
        return Optional.empty();
    }

    public Map<UUID, String> getAll() {
        purgeExpired();
        Map<UUID, String> snapshot = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, AffinityEntry> entry : affinityMap.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().serverName());
        }
        return Map.copyOf(snapshot);
    }

    public void purgeExpired() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, AffinityEntry> entry : affinityMap.entrySet()) {
            if (isExpired(entry.getValue(), now)) {
                affinityMap.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public void clear() {
        affinityMap.clear();
    }

    private boolean isExpired(AffinityEntry entry, Instant now) {
        return entry.updatedAt().plus(ttl).isBefore(now);
    }

    private record AffinityEntry(String serverName, Instant updatedAt) {
    }
}
