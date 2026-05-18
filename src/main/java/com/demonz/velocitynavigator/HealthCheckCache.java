package com.demonz.velocitynavigator;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class HealthCheckCache {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public Entry getIfFresh(String key, Instant now, Duration ttl) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        if (entry.checkedAt().plus(ttl).isBefore(now)) {
            entries.remove(key, entry);
            return null;
        }
        return entry;
    }

    public void put(String key, boolean online, Instant checkedAt) {
        entries.put(key, new Entry(online, checkedAt));
    }

    /**
     * Get a cached entry regardless of freshness (used for getCachedOnlineServers).
     */
    public Entry getCached(String key) {
        return entries.get(key);
    }

    /**
     * Purge entries whose checkedAt is before the cutoff time.
     */
    public void purgeExpired(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        Instant cutoff = Instant.now().minus(ttl);
        entries.entrySet().removeIf(entry -> entry.getValue().checkedAt().isBefore(cutoff));
    }

    public void clear() {
        entries.clear();
    }

    public ConcurrentMap<String, Entry> entries() {
        return entries;
    }

    public record Entry(boolean online, Instant checkedAt) {
    }
}
