package com.demonz.velocitynavigator;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RoutingStats {

    private final ConcurrentMap<String, AtomicLong> connectionCounts = new ConcurrentHashMap<>();
    private volatile Instant lastReset = Instant.now();

    public void recordConnection(String serverName) {
        connectionCounts.computeIfAbsent(serverName, k -> new AtomicLong(0)).incrementAndGet();
    }

    public Map<String, Long> getDistribution() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : connectionCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    /**
     * Returns the total number of connections recorded since the last reset.
     * Used by the degraded round-robin fallback to rotate through servers.
     */
    public long totalConnections() {
        long total = 0;
        for (AtomicLong count : connectionCounts.values()) {
            total += count.get();
        }
        return total;
    }

    public Instant lastReset() {
        return lastReset;
    }

    public void reset() {
        connectionCounts.clear();
        lastReset = Instant.now();
    }
}
