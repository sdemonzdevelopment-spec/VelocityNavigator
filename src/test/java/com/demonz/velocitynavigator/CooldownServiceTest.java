package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownServiceTest {

    @Test
    void secondsRemainingReturnsEmptyWhenExactlyExpired() throws InterruptedException {
        CooldownService service = new CooldownService();
        UUID playerId = UUID.randomUUID();

        service.apply(playerId, 1);
        // Sleep past the 1-second cooldown
        Thread.sleep(1100);

        OptionalLong remaining = service.secondsRemaining(playerId);
        assertTrue(remaining.isEmpty(), "Cooldown should be expired after 1100ms for a 1-second cooldown");
    }

    @Test
    void secondsRemainingNeverReturnsZero() {
        CooldownService service = new CooldownService();
        UUID playerId = UUID.randomUUID();

        service.apply(playerId, 60);
        OptionalLong remaining = service.secondsRemaining(playerId);

        assertTrue(remaining.isPresent());
        assertTrue(remaining.getAsLong() >= 1,
                "secondsRemaining should never return zero; got " + remaining.getAsLong());
    }

    @Test
    void secondsRemainingCapturesInstantOnce() {
        // Verifies the single-Instant.now() capture fix: the same instant is used
        // for both the expiry check and the Duration calculation, so the result
        // is always consistent and never negative/zero.
        CooldownService service = new CooldownService();
        UUID playerId = UUID.randomUUID();

        // Apply a very short cooldown and immediately query — there should be no
        // race between the check and the duration calculation.
        service.apply(playerId, 2);

        // Query rapidly many times; none should return a value < 1
        for (int i = 0; i < 100; i++) {
            OptionalLong remaining = service.secondsRemaining(playerId);
            if (remaining.isPresent()) {
                assertTrue(remaining.getAsLong() >= 1,
                        "secondsRemaining should never return < 1; got " + remaining.getAsLong() + " on iteration " + i);
            }
        }
    }
}
