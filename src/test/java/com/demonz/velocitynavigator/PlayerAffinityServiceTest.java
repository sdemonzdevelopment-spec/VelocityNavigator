package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
    @Disabled("PlayerAffinityService does not implement TTL-based expiry. "
            + "The current implementation stores affinity indefinitely with no inactivity timeout. "
            + "This test is a placeholder for when TTL support is added.")
    void affinityExpiresAfterInactivity() {
        // Test would verify that after a configurable TTL period of inactivity,
        // the player's affinity is cleared. Currently not implemented.
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
}
