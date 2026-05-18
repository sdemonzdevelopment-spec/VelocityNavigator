package com.demonz.velocitynavigator;

public record ServerCandidate(String name, int playerCount, int effectiveWeight, double emaLoad) {

    public ServerCandidate(String name, int playerCount) {
        this(name, playerCount, Config.LobbyEntry.DEFAULT_WEIGHT, playerCount);
    }

    public ServerCandidate(String name, int playerCount, int effectiveWeight) {
        this(name, playerCount, effectiveWeight, playerCount);
    }
}
