package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyEntryTest {

    @Test
    void uncappedByDefault() {
        // When maxPlayers is negative, the compact constructor normalizes it to UNCAPPED (-1)
        Config.LobbyEntry entry = new Config.LobbyEntry("lobby-1", -5, 1);
        assertEquals(Config.LobbyEntry.UNCAPPED, entry.maxPlayers(),
                "Negative maxPlayers should be normalized to UNCAPPED");
    }

    @Test
    void isFullReturnsTrueWhenAtCap() {
        Config.LobbyEntry entry = new Config.LobbyEntry("lobby-1", 50, 1);

        assertTrue(entry.isFull(50), "Should be full when currentPlayers == maxPlayers");
        assertTrue(entry.isFull(60), "Should be full when currentPlayers > maxPlayers");
        assertFalse(entry.isFull(49), "Should not be full when currentPlayers < maxPlayers");
    }

    @Test
    void isFullReturnsFalseWhenUncapped() {
        Config.LobbyEntry entry = new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, 1);

        assertFalse(entry.isFull(0), "UNCAPPED should never be full");
        assertFalse(entry.isFull(1000), "UNCAPPED should never be full, even with many players");
    }

    @Test
    void defaultWeightIsOne() {
        // When weight is 0 or negative, the compact constructor normalizes it to DEFAULT_WEIGHT (1)
        Config.LobbyEntry entryZero = new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, 0);
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, entryZero.weight(),
                "Zero weight should default to DEFAULT_WEIGHT (1)");

        Config.LobbyEntry entryNeg = new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, -5);
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, entryNeg.weight(),
                "Negative weight should default to DEFAULT_WEIGHT (1)");
    }

    @Test
    void negativeValuesDefaultToConstants() {
        Config.LobbyEntry entry = new Config.LobbyEntry("test", -1, 0);

        // -1 for maxPlayers is UNCAPPED (which is -1), so it stays as -1
        assertEquals(Config.LobbyEntry.UNCAPPED, entry.maxPlayers(),
                "Negative max_players should be UNCAPPED (-1)");
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, entry.weight(),
                "Zero/negative weight should be DEFAULT_WEIGHT (1)");
    }
}
