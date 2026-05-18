package com.demonz.velocitynavigator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DrainService {

    private final ConcurrentMap<String, Boolean> drainState = new ConcurrentHashMap<>();

    /**
     * Checks if a server is drained. Uses single atomic get() instead of
     * containsKey()+get() to prevent NPE from concurrent undrain operations.
     */
    public boolean isDrained(String serverName) {
        Boolean state = drainState.get(serverName);
        return state != null && state;
    }

    public void drain(String serverName) {
        drainState.put(serverName, Boolean.TRUE);
    }

    public void undrain(String serverName) {
        drainState.remove(serverName);
    }

    public ConcurrentMap<String, Boolean> drainState() {
        return new ConcurrentHashMap<>(drainState);
    }

    public void clear() {
        drainState.clear();
    }
}
