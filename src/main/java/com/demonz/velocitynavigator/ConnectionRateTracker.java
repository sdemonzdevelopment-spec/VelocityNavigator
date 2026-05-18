package com.demonz.velocitynavigator;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ConnectionRateTracker {

    private final int windowSeconds;
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Instant>> connectionTimes = new ConcurrentHashMap<>();

    public ConnectionRateTracker(int windowSeconds) {
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public void recordConnection(String serverName) {
        ConcurrentLinkedDeque<Instant> times = connectionTimes.computeIfAbsent(serverName, k -> new ConcurrentLinkedDeque<>());
        times.addLast(Instant.now());
        purgeOld(times);
    }

    public double getRatePerSecond(String serverName) {
        ConcurrentLinkedDeque<Instant> times = connectionTimes.get(serverName);
        if (times == null || times.isEmpty()) {
            return 0.0;
        }
        purgeOld(times);
        long count = times.size();
        if (count <= 1) {
            return 0.0;
        }
        Instant oldest = times.peekFirst();
        if (oldest == null) {
            return 0.0;
        }
        double spanSeconds = Duration.between(oldest, Instant.now()).toMillis() / 1000.0;
        return spanSeconds > 0 ? count / spanSeconds : 0.0;
    }

    public int getConnectionCount(String serverName) {
        ConcurrentLinkedDeque<Instant> times = connectionTimes.get(serverName);
        if (times == null) {
            return 0;
        }
        purgeOld(times);
        return times.size();
    }

    public void remove(String serverName) {
        connectionTimes.remove(serverName);
    }

    public void clear() {
        connectionTimes.clear();
    }

    private void purgeOld(ConcurrentLinkedDeque<Instant> times) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        while (!times.isEmpty()) {
            Instant oldest = times.peekFirst();
            if (oldest != null && oldest.isBefore(cutoff)) {
                times.pollFirst();
            } else {
                break;
            }
        }
    }
}
