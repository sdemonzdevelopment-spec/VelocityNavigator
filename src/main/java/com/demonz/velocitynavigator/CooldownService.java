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
        Instant now = Instant.now();
        if (!now.isBefore(expiresAt)) {
            cooldowns.remove(playerId, expiresAt);
            return OptionalLong.empty();
        }
        long seconds = Duration.between(now, expiresAt).toSeconds() + 1;
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

