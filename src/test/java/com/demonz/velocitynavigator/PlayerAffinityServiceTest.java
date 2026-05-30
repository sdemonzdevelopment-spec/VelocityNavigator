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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAffinityServiceTest {

    @Test
    void recordsAndRetrievesAffinity() {
        PlayerAffinityService service = new PlayerAffinityService(1.0);
        UUID playerId = UUID.randomUUID();

        service.setAffinity(playerId, "lobby-1");
        Optional<String> affinity = service.getAffinity(playerId);

        assertTrue(affinity.isPresent());
        assertEquals("lobby-1", affinity.get());
    }

    @Test
    void clearRemovesAffinity() {
        PlayerAffinityService service = new PlayerAffinityService(1.0);
        UUID playerId = UUID.randomUUID();

        service.setAffinity(playerId, "lobby-1");
        assertTrue(service.getAffinity(playerId).isPresent());

        service.removeAffinity(playerId);
        assertFalse(service.getAffinity(playerId).isPresent(),
                "Affinity should be removed after clear");
    }

    @Test
    void expiredAffinityIsPurged() throws InterruptedException {
        PlayerAffinityService service = new PlayerAffinityService(1.0, Duration.ofMillis(1));
        UUID playerId = UUID.randomUUID();

        service.setAffinity(playerId, "lobby-1");
        Thread.sleep(10);

        assertFalse(service.getAffinity(playerId).isPresent());
        assertTrue(service.getAll().isEmpty());
    }
}
