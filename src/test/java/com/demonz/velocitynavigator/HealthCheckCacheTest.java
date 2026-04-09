package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
