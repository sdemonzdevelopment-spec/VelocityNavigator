package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {

    @Test
    void fallsBackToDefaultLobbiesWhenContextualGroupIsOffline() {
        Config config = new Config(
                3,
                Config.defaults().commands(),
                new Config.Routing(
                        Config.SelectionMode.LEAST_PLAYERS,
                        true,
                        true,
                        List.of("lobby-1"),
                        new Config.Contextual(
                                true,
                                true,
                                Map.of("bedwars", List.of("bedwars-lobby-1")),
                                Map.of("bedwars-1", "bedwars")
                        )
                ),
                Config.defaults().healthChecks(),
                Config.defaults().messages(),
                Config.defaults().updateChecker(),
                Config.defaults().metrics(),
                Config.defaults().debug()
        );

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy(new Random(0)));
        RouteDecision decision = planner.plan("bedwars-1", config, Map.of("lobby-1", 4));

        assertEquals("bedwars", decision.requestedGroup());
        assertEquals("default", decision.usedGroup());
        assertEquals("lobby-1", decision.selectedServer());
        assertTrue(decision.fallbackToDefault());
    }

    @Test
    void routesCorrectlyWithMixedCaseSourceServer() {
        Config config = new Config(
                3,
                Config.defaults().commands(),
                new Config.Routing(
                        Config.SelectionMode.LEAST_PLAYERS,
                        true,
                        true,
                        List.of("lobby-1", "lobby-2"),
                        Config.defaults().routing().contextual()
                ),
                Config.defaults().healthChecks(),
                Config.defaults().messages(),
                Config.defaults().updateChecker(),
                Config.defaults().metrics(),
                Config.defaults().debug()
        );

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy(new Random(0)));
        // Source server has uppercase letters, config lobbies are lowercase.
        // With cycle_when_possible=true, the planner must still remove the current server.
        RouteDecision decision = planner.plan("Lobby-1", config, Map.of("Lobby-1", 10, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
    }

    @Test
    void contextualRoutingMatchesWithMixedCaseSourceServer() {
        Config config = new Config(
                3,
                Config.defaults().commands(),
                new Config.Routing(
                        Config.SelectionMode.LEAST_PLAYERS,
                        false,
                        true,
                        List.of("lobby-1"),
                        new Config.Contextual(
                                true,
                                true,
                                Map.of("bedwars", List.of("bw-lobby-1", "bw-lobby-2")),
                                Map.of("bedwars-1", "bedwars")
                        )
                ),
                Config.defaults().healthChecks(),
                Config.defaults().messages(),
                Config.defaults().updateChecker(),
                Config.defaults().metrics(),
                Config.defaults().debug()
        );

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy(new Random(0)));
        // Source server has uppercase — contextual sources key is lowercase "bedwars-1"
        RouteDecision decision = planner.plan("Bedwars-1", config, Map.of("bw-lobby-1", 3, "bw-lobby-2", 7));

        assertEquals("bedwars", decision.requestedGroup());
        assertEquals("bedwars", decision.usedGroup());
        assertTrue(decision.hasSelection());
        assertEquals("bw-lobby-1", decision.selectedServer());
        assertFalse(decision.fallbackToDefault());
    }

    @Test
    void cycleWhenPossibleWorksWithSingleLobby() {
        Config config = new Config(
                3,
                Config.defaults().commands(),
                new Config.Routing(
                        Config.SelectionMode.LEAST_PLAYERS,
                        true,
                        true,
                        List.of("lobby-1"),
                        Config.defaults().routing().contextual()
                ),
                Config.defaults().healthChecks(),
                Config.defaults().messages(),
                Config.defaults().updateChecker(),
                Config.defaults().metrics(),
                Config.defaults().debug()
        );

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy(new Random(0)));
        // Only one lobby online and it's the one the player is on.
        // cycle_when_possible should NOT remove it since there's no alternative.
        RouteDecision decision = planner.plan("lobby-1", config, Map.of("lobby-1", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-1", decision.selectedServer());
    }
}
