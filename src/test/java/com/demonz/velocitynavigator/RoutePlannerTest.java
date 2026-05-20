package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {

    private static Config.Routing defaultRouting() {
        return Config.defaults().routing();
    }

    private static Config baseConfig(Config.Routing routing) {
        Config d = Config.defaults();
        return new Config(
                Config.CURRENT_VERSION,
                d.commands(),
                routing,
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                d.notifyOnStartup(),
                d.notifyAdminsOnJoin()
        );
    }

    @Test
    void fallsBackToDefaultLobbiesWhenContextualGroupIsOffline() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                true,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        true,
                        Map.of("bedwars", new Config.GroupConfig(
                                List.of(new Config.LobbyEntry("bedwars-lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                                null
                        )),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of()
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("bedwars-1", config, Map.of("lobby-1", 4));

        assertEquals("bedwars", decision.requestedGroup());
        assertEquals("default", decision.usedGroup());
        assertEquals("lobby-1", decision.selectedServer());
        assertTrue(decision.fallbackToDefault());
    }

    @Test
    void routesCorrectlyWithMixedCaseSourceServer() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                true,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("Lobby-1", config, Map.of("Lobby-1", 10, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
    }

    @Test
    void contextualRoutingMatchesWithMixedCaseSourceServer() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        true,
                        Map.of("bedwars", new Config.GroupConfig(
                                List.of(
                                        new Config.LobbyEntry("bw-lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                                        new Config.LobbyEntry("bw-lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                                ),
                                null
                        )),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of()
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("Bedwars-1", config, Map.of("bw-lobby-1", 3, "bw-lobby-2", 7));

        assertEquals("bedwars", decision.requestedGroup());
        assertEquals("bedwars", decision.usedGroup());
        assertTrue(decision.hasSelection());
        assertEquals("bw-lobby-1", decision.selectedServer());
        assertFalse(decision.fallbackToDefault());
    }

    @Test
    void cycleWhenPossibleWorksWithSingleLobby() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                true,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("lobby-1", config, Map.of("lobby-1", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-1", decision.selectedServer());
    }

    // --- New v4 tests ---

    @Test
    void drainedServersExcludedFromCandidates() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        DrainService drainService = new DrainService();
        drainService.drain("lobby-1");
        planner.setDrainService(drainService);

        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 5, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
        assertFalse(decision.onlineCandidates().contains("lobby-1"));
    }

    @Test
    void circuitBreakerOpenServersExcluded() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        CircuitBreaker breaker = new CircuitBreaker(3, 30, 1);
        // Force lobby-1 into OPEN state
        breaker.recordFailure("lobby-1");
        breaker.recordFailure("lobby-1");
        breaker.recordFailure("lobby-1");
        planner.setCircuitBreaker(breaker);

        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 5, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
        assertFalse(decision.onlineCandidates().contains("lobby-1"));
    }

    @Test
    void perGroupSelectionModeOverride() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        false,
                        Map.of("bedwars", new Config.GroupConfig(
                                List.of(
                                        new Config.LobbyEntry("bw-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                                        new Config.LobbyEntry("bw-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                                ),
                                Config.SelectionMode.ROUND_ROBIN  // Override: use round-robin for this group
                        )),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of()
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("bedwars-1", config, Map.of("bw-1", 10, "bw-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals(Config.SelectionMode.ROUND_ROBIN, decision.selectionMode());
        assertEquals("bedwars", decision.usedGroup());
    }

    @Test
    void fallbackChainWalksCorrectly() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        true,
                        Map.of(
                                "bedwars", new Config.GroupConfig(
                                        List.of(new Config.LobbyEntry("bw-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                                        null
                                ),
                                "skywars", new Config.GroupConfig(
                                        List.of(new Config.LobbyEntry("sw-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                                        null
                                )
                        ),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of("bedwars", List.of("skywars"))  // bedwars falls back to skywars
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        // bedwars-1 maps to "bedwars", but bw-1 is offline. Fallback chain should try "skywars".
        RouteDecision decision = planner.plan("bedwars-1", config, Map.of("sw-1", 5));

        assertTrue(decision.hasSelection());
        assertEquals("sw-1", decision.selectedServer());
        assertTrue(decision.fallbackToDefault());
    }

    @Test
    void playerAffinityPreferredWhenAvailable() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        PlayerAffinityService affinityService = new PlayerAffinityService(1.0); // 100% stickiness
        UUID playerId = UUID.randomUUID();
        affinityService.setAffinity(playerId, "lobby-2");
        planner.setAffinityService(affinityService);

        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 0, "lobby-2", 50), playerId);

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
    }

    @Test
    void lobbyEntryCapExcludesFullServers() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", 10, Config.LobbyEntry.DEFAULT_WEIGHT),  // max 10 players
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        // lobby-1 is at capacity (10/10), should be excluded
        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 10, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
        assertFalse(decision.onlineCandidates().contains("lobby-1"));
    }

    @Test
    void consistentHashFallsBackToLeastPlayersWhenPlayerIdIsNull() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.CONSISTENT_HASH,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        ConsistentHashRing hashRing = new ConsistentHashRing();
        planner.setHashRing(hashRing);

        // Player ID is null
        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 10, "lobby-2", 5), null);

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer()); // Least players fallback
        assertTrue(decision.reason().contains("Consistent hash selection was unavailable or failed; fell back to LEAST_PLAYERS"));
    }
}
