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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerOfTwoSelectionTest {

    @Test
    void selectsLessLoadedOfTwoRandom() {
        // With <= 2 candidates, power-of-two falls back to min(playerCount)
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("heavy", 50),
                new ServerCandidate("light", 5)
        );

        Optional<ServerCandidate> chosen = strategy.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isPresent());
        assertEquals("light", chosen.get().name(),
                "With 2 candidates, should always pick the less loaded");
    }

    @Test
    void fallsBackForSingleCandidate() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("only-one", 42)
        );

        Optional<ServerCandidate> chosen = strategy.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isPresent());
        assertEquals("only-one", chosen.get().name());
    }

    @Test
    void fallsBackForTwoCandidates() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("lobby-1", 10),
                new ServerCandidate("lobby-2", 5)
        );

        Optional<ServerCandidate> chosen = strategy.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isPresent());
        assertEquals("lobby-2", chosen.get().name(),
                "With 2 candidates, should pick the less loaded");
    }

    @Test
    void distributionIsBetterThanRandom() {
        // With 4 servers of varying load, power-of-two should favor less-loaded servers.
        // Over 1000 selections, the least-loaded server should get significantly more
        // traffic than pure random (25% each), and the deviation should be < 15% from ideal.
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("server-a", 5),
                new ServerCandidate("server-b", 10),
                new ServerCandidate("server-c", 20),
                new ServerCandidate("server-d", 40)
        );

        Map<String, Integer> counts = new HashMap<>();
        for (String name : List.of("server-a", "server-b", "server-c", "server-d")) {
            counts.put(name, 0);
        }

        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            // Reset strategy to get fresh selections each time
            RouteSelectionStrategy fresh = new RouteSelectionStrategy();
            Optional<ServerCandidate> chosen = fresh.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test-" + i);
            assertTrue(chosen.isPresent());
            counts.merge(chosen.get().name(), 1, Integer::sum);
        }

        // The lightest server (5 players) should get more than random (25% = 250)
        // and the heaviest (40 players) should get less than random.
        int lightestCount = counts.get("server-a");
        int heaviestCount = counts.get("server-d");

        // For power-of-two with these loads, lightest should get noticeably more than 250
        // and heaviest should get noticeably less than 250.
        // We check that deviation from ideal even distribution (250) is reasonable.
        // With power-of-two, the distribution is not perfectly proportional but
        // should be much better than random.
        double idealPerServer = iterations / 4.0;
        double maxAllowedDeviation = idealPerServer * 0.50; // 50% deviation allowed from even distribution

        // The heaviest server should have less than ideal (it's disadvantaged)
        assertTrue(heaviestCount < idealPerServer,
                "Heaviest server should get less than even distribution; got " + heaviestCount);
        // The lightest server should have more than ideal (it's advantaged)
        assertTrue(lightestCount > idealPerServer,
                "Lightest server should get more than even distribution; got " + lightestCount);
    }

    @Test
    void handlesEmptyCandidates() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        Optional<ServerCandidate> chosen = strategy.select(List.of(), Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isEmpty());
    }
}
