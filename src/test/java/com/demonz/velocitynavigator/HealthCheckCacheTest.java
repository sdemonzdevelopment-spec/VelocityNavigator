package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckCacheTest {

    @Test
    void returnsFreshEntriesBeforeExpiry() {
        HealthCheckCache cache = new HealthCheckCache();
        Instant checkedAt = Instant.parse("2026-01-01T00:00:00Z");
        cache.put("lobby-1", true, checkedAt);

        HealthCheckCache.Entry entry = cache.getIfFresh("lobby-1", checkedAt.plusSeconds(5), Duration.ofSeconds(10));

        assertNotNull(entry);
    }

    @Test
    void expiresEntriesAfterTheTtl() {
        HealthCheckCache cache = new HealthCheckCache();
        Instant checkedAt = Instant.parse("2026-01-01T00:00:00Z");
        cache.put("lobby-1", true, checkedAt);

        HealthCheckCache.Entry entry = cache.getIfFresh("lobby-1", checkedAt.plusSeconds(30), Duration.ofSeconds(10));

        assertNull(entry);
    }

    @Test
    void purgeExpiredRemovesStaleEntries() {
        HealthCheckCache cache = new HealthCheckCache();
        Instant now = Instant.now();
        // Old entry — checked 2 hours ago
        cache.put("old-server", true, now.minusSeconds(7200));
        // Fresh entry — checked just now
        cache.put("fresh-server", true, now.minusSeconds(10));

        cache.purgeExpired(Duration.ofMinutes(60)); // TTL = 1 hour

        // Old entry should be purged
        assertNull(cache.getCached("old-server"),
                "Entry older than TTL should be purged");
        // Fresh entry should remain
        assertNotNull(cache.getCached("fresh-server"),
                "Entry newer than TTL should remain");
    }

    @Test
    void purgeExpiredWithZeroTtlDoesNothing() {
        HealthCheckCache cache = new HealthCheckCache();
        cache.put("server-1", true, Instant.now());

        // Zero TTL — should not remove anything (early return)
        cache.purgeExpired(Duration.ZERO);

        assertNotNull(cache.getCached("server-1"),
                "Zero TTL should not remove entries");
    }
}
