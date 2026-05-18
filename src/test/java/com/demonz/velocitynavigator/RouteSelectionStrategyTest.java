package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteSelectionStrategyTest {

    @Test
    void leastPlayersPrefersTheSmallestServer() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        Optional<ServerCandidate> chosen = strategy.select(
                List.of(
                        new ServerCandidate("lobby-1", 30),
                        new ServerCandidate("lobby-2", 10),
                        new ServerCandidate("lobby-3", 20)
                ),
                Config.SelectionMode.LEAST_PLAYERS,
                "default"
        );

        assertTrue(chosen.isPresent());
        assertEquals("lobby-2", chosen.get().name());
    }

    @Test
    void roundRobinCyclesPerGroup() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("lobby-1", 10),
                new ServerCandidate("lobby-2", 10),
                new ServerCandidate("lobby-3", 10)
        );

        assertEquals("lobby-1", strategy.select(candidates, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(candidates, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-3", strategy.select(candidates, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
    }

    @Test
    void userScenarioTest() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();

        List<ServerCandidate> servers = List.of(
                new ServerCandidate("lobby-1", 0),
                new ServerCandidate("lobby-2", 0)
        );

        // MODE: ROUND_ROBIN — distribution: 1, 2, 1, 2
        assertEquals("lobby-1", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-1", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());

        // MODE: LEAST_PLAYERS
        // Player 1 — both at 0, picks first by name
        assertEquals("lobby-1", strategy.select(List.of(
            new ServerCandidate("lobby-1", 0), new ServerCandidate("lobby-2", 0)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        // Player 2 sees lobby-1 has 1 player
        assertEquals("lobby-2", strategy.select(List.of(
            new ServerCandidate("lobby-1", 1), new ServerCandidate("lobby-2", 0)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        // Player 3 sees both have 1 player (prefers first by name)
        assertEquals("lobby-1", strategy.select(List.of(
            new ServerCandidate("lobby-1", 1), new ServerCandidate("lobby-2", 1)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        // Player 4 sees lobby-1 has 2, lobby-2 has 1
        assertEquals("lobby-2", strategy.select(List.of(
            new ServerCandidate("lobby-1", 2), new ServerCandidate("lobby-2", 1)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());
    }
}
