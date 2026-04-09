package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteSelectionStrategyTest {

    @Test
    void leastPlayersPrefersTheSmallestServer() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy(new Random(0));
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
    void randomUsesTheInjectedRandomInstance() {
        Random deterministic = new Random() {
            @Override
            public int nextInt(int bound) {
                return 1;
            }
        };
        RouteSelectionStrategy strategy = new RouteSelectionStrategy(deterministic);
        Optional<ServerCandidate> chosen = strategy.select(
                List.of(
                        new ServerCandidate("lobby-1", 10),
                        new ServerCandidate("lobby-2", 10),
                        new ServerCandidate("lobby-3", 10)
                ),
                Config.SelectionMode.RANDOM,
                "default"
        );

        assertTrue(chosen.isPresent());
        assertEquals("lobby-2", chosen.get().name());
    }

    @Test
    void roundRobinCyclesPerGroup() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy(new Random(0));
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
        RouteSelectionStrategy strategy = new RouteSelectionStrategy(new Random(0));
        
        // Scenario: 4 players run /lobby simultaneously.
        // We simulate this by calling select 4 times.
        
        List<ServerCandidate> servers = List.of(
                new ServerCandidate("lobby-1", 0),
                new ServerCandidate("lobby-2", 0)
        );

        // MODE: ROUND_ROBIN
        // Distribution should be: 1, 2, 1, 2
        assertEquals("lobby-1", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-1", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());

        // MODE: LEAST_PLAYERS
        // Note: For Least Players, we simulate the "increments" in the counts as they join.
        // Player 1
        assertEquals("lobby-1", strategy.select(List.of(
            new ServerCandidate("lobby-1", 0), new ServerCandidate("lobby-2", 0)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());
        
        // Player 2 sees lobby-1 has 1 player
        assertEquals("lobby-2", strategy.select(List.of(
            new ServerCandidate("lobby-1", 1), new ServerCandidate("lobby-2", 0)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        // Player 3 sees both have 1 player (prefers alphabetically/order or min)
        assertEquals("lobby-1", strategy.select(List.of(
            new ServerCandidate("lobby-1", 1), new ServerCandidate("lobby-2", 1)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        // Player 4 sees lobby-1 has 2, lobby-2 has 1
        assertEquals("lobby-2", strategy.select(List.of(
            new ServerCandidate("lobby-1", 2), new ServerCandidate("lobby-2", 1)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());
    }
}

