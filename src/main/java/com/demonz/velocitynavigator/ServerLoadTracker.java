package com.demonz.velocitynavigator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ServerLoadTracker {

    private final double smoothingFactor;
    private final ConcurrentMap<String, Double> emaValues = new ConcurrentHashMap<>();

    public ServerLoadTracker(double smoothingFactor) {
        this.smoothingFactor = Math.max(0.01, Math.min(1.0, smoothingFactor));
    }

    public void update(String serverName, int currentPlayers) {
        emaValues.compute(serverName, (key, previous) -> {
            if (previous == null) {
                return (double) currentPlayers;
            }
            return smoothingFactor * currentPlayers + (1.0 - smoothingFactor) * previous;
        });
    }

    public double getEma(String serverName) {
        return emaValues.getOrDefault(serverName, 0.0);
    }

    public void remove(String serverName) {
        emaValues.remove(serverName);
    }

    public void clear() {
        emaValues.clear();
    }
}
