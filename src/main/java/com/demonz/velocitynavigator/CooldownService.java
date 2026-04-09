package com.demonz.velocitynavigator;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CooldownService {

    private final ConcurrentMap<UUID, Instant> cooldowns = new ConcurrentHashMap<>();

    public OptionalLong secondsRemaining(UUID playerId) {
        Instant expiresAt = cooldowns.get(playerId);
        if (expiresAt == null) {
            return OptionalLong.empty();
        }
        if (!Instant.now().isBefore(expiresAt)) {
            cooldowns.remove(playerId, expiresAt);
            return OptionalLong.empty();
        }
        long seconds = Duration.between(Instant.now(), expiresAt).toSeconds() + 1;
        return OptionalLong.of(seconds);
    }

    public void apply(UUID playerId, int seconds) {
        if (seconds <= 0) {
            return;
        }
        cooldowns.put(playerId, Instant.now().plusSeconds(seconds));
    }

    public void clear(UUID playerId) {
        cooldowns.remove(playerId);
    }
}

