package com.demonz.velocitynavigator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConsistentHashRing {

    private static final int DEFAULT_VIRTUAL_NODES = 150;

    private final int virtualNodes;
    private final ConcurrentMap<String, NavigableMap<Long, String>> rings = new ConcurrentHashMap<>();
    // FIX-6: Cache the previous server list per group to avoid unnecessary ring rebuilds
    private final ConcurrentMap<String, List<String>> previousServerLists = new ConcurrentHashMap<>();

    public ConsistentHashRing() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = Math.max(1, virtualNodes);
    }

    public void updateRing(String groupKey, List<String> servers) {
        // FIX-6: Skip rebuild if the server list hasn't changed
        List<String> sortedServers = new ArrayList<>(servers);
        Collections.sort(sortedServers);
        List<String> previous = previousServerLists.get(groupKey);
        if (previous != null && previous.equals(sortedServers)) {
            return; // Ring is already up-to-date for this group
        }

        NavigableMap<Long, String> ring = new TreeMap<>();
        for (String server : servers) {
            for (int i = 0; i < virtualNodes; i++) {
                long hash = hash(groupKey + ":" + server + ":" + i);
                ring.put(hash, server);
            }
        }
        // FIX-5: Store an immutable snapshot so concurrent reads never see a partially-built TreeMap
        rings.put(groupKey, Collections.unmodifiableNavigableMap(ring));
        previousServerLists.put(groupKey, sortedServers);
    }

    public String getServer(String groupKey, String input) {
        NavigableMap<Long, String> ring = rings.get(groupKey);
        if (ring == null || ring.isEmpty()) {
            return null;
        }
        long hash = hash(input);
        NavigableMap<Long, String> tail = ring.tailMap(hash, true);
        Map.Entry<Long, String> entry = tail.isEmpty() ? ring.firstEntry() : tail.firstEntry();
        return entry.getValue();
    }

    public List<String> getServerOrder(String groupKey, String input) {
        NavigableMap<Long, String> ring = rings.get(groupKey);
        if (ring == null || ring.isEmpty()) {
            return List.of();
        }
        long hash = hash(input);
        List<String> result = new ArrayList<>();
        NavigableMap<Long, String> tail = ring.tailMap(hash, true);
        for (String server : tail.values()) {
            if (!result.contains(server)) {
                result.add(server);
            }
        }
        for (String server : ring.headMap(hash, false).values()) {
            if (!result.contains(server)) {
                result.add(server);
            }
        }
        return result;
    }

    public void removeGroup(String groupKey) {
        rings.remove(groupKey);
    }

    public void clear() {
        rings.clear();
        previousServerLists.clear();
    }

    private long hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return ((long) (digest[0] & 0xFF) << 56)
                    | ((long) (digest[1] & 0xFF) << 48)
                    | ((long) (digest[2] & 0xFF) << 40)
                    | ((long) (digest[3] & 0xFF) << 32)
                    | ((long) (digest[4] & 0xFF) << 24)
                    | ((long) (digest[5] & 0xFF) << 16)
                    | ((long) (digest[6] & 0xFF) << 8)
                    | ((long) (digest[7] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist
            throw new RuntimeException(e);
        }
    }
}
