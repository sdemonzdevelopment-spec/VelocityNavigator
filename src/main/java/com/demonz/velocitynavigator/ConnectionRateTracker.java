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
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ConnectionRateTracker {

    private static final int DEFAULT_MAX_ENTRIES_PER_SERVER = 10_000;

    private final int windowSeconds;
    private final int maxEntriesPerServer;
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Instant>> connectionTimes = new ConcurrentHashMap<>();

    public ConnectionRateTracker(int windowSeconds) {
        this(windowSeconds, DEFAULT_MAX_ENTRIES_PER_SERVER);
    }

    ConnectionRateTracker(int windowSeconds, int maxEntriesPerServer) {
        this.windowSeconds = Math.max(1, windowSeconds);
        this.maxEntriesPerServer = Math.max(1, maxEntriesPerServer);
    }

    public void recordConnection(String serverName) {
        String normalizedServerName = normalize(serverName);
        if (normalizedServerName.isBlank()) {
            return;
        }
        ConcurrentLinkedDeque<Instant> times = connectionTimes.computeIfAbsent(normalizedServerName, k -> new ConcurrentLinkedDeque<>());
        times.addLast(Instant.now());
        purgeOld(times);
        trimToLimit(times);
    }

    public double getRatePerSecond(String serverName) {
        ConcurrentLinkedDeque<Instant> times = connectionTimes.get(normalize(serverName));
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
        ConcurrentLinkedDeque<Instant> times = connectionTimes.get(normalize(serverName));
        if (times == null) {
            return 0;
        }
        purgeOld(times);
        return times.size();
    }

    public void remove(String serverName) {
        connectionTimes.remove(normalize(serverName));
    }

    public void retainServers(Collection<String> serverNames) {
        if (serverNames == null) {
            clear();
            return;
        }
        java.util.Set<String> retained = new java.util.HashSet<>();
        for (String serverName : serverNames) {
            String normalized = normalize(serverName);
            if (!normalized.isBlank()) {
                retained.add(normalized);
            }
        }
        connectionTimes.keySet().removeIf(key -> !retained.contains(key));
    }

    public void purge() {
        for (Map.Entry<String, ConcurrentLinkedDeque<Instant>> entry : connectionTimes.entrySet()) {
            ConcurrentLinkedDeque<Instant> times = entry.getValue();
            purgeOld(times);
            if (times.isEmpty()) {
                connectionTimes.remove(entry.getKey(), times);
            }
        }
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

    private void trimToLimit(ConcurrentLinkedDeque<Instant> times) {
        while (times.size() > maxEntriesPerServer) {
            times.pollFirst();
        }
    }

    private String normalize(String serverName) {
        return serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
    }
}
