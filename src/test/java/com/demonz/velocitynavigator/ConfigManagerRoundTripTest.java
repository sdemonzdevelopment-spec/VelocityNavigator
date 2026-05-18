package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void configRoundTripPreservesAllFields() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));

        // Load to create default config
        ConfigLoadResult firstLoad = manager.load();
        assertTrue(firstLoad.createdDefault());

        // Load again to read back
        ConfigLoadResult secondLoad = manager.load();
        Config config = secondLoad.config();

        // Verify all default fields match
        Config defaults = Config.defaults();
        assertEquals(defaults.configVersion(), config.configVersion());
        assertEquals(defaults.commands().primary(), config.commands().primary());
        assertEquals(defaults.commands().aliases(), config.commands().aliases());
        assertEquals(defaults.commands().cooldownSeconds(), config.commands().cooldownSeconds());
        assertEquals(defaults.routing().selectionMode(), config.routing().selectionMode());
        assertEquals(defaults.routing().cycleWhenPossible(), config.routing().cycleWhenPossible());
        assertEquals(defaults.routing().maxRetries(), config.routing().maxRetries());
        assertEquals(defaults.healthChecks().enabled(), config.healthChecks().enabled());
        assertEquals(defaults.healthChecks().timeoutMs(), config.healthChecks().timeoutMs());
        assertEquals(defaults.healthChecks().cacheSeconds(), config.healthChecks().cacheSeconds());
        assertEquals(defaults.updateChecker().channel(), config.updateChecker().channel());
        assertEquals(defaults.circuitBreaker().enabled(), config.circuitBreaker().enabled());
        assertEquals(defaults.circuitBreaker().failureThreshold(), config.circuitBreaker().failureThreshold());
        assertEquals(defaults.circuitBreaker().cooldownSeconds(), config.circuitBreaker().cooldownSeconds());
        assertEquals(defaults.circuitBreaker().halfOpenMaxTests(), config.circuitBreaker().halfOpenMaxTests());
        assertEquals(defaults.degradation().enabled(), config.degradation().enabled());
        assertEquals(defaults.degradation().mode(), config.degradation().mode());
        assertEquals(defaults.notifyOnStartup(), config.notifyOnStartup());
        assertEquals(defaults.notifyAdminsOnJoin(), config.notifyAdminsOnJoin());
    }

    @Test
    void contextualGroupKeyWithSpaceIsQuotedCorrectly() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 4

                [commands]
                aliases = ["hub"]

                [routing]
                default_lobbies = ["lobby-1"]

                [routing.contextual]
                enabled = true
                fallback_to_default = true

                [routing.contextual.groups]
                "bed wars" = ["bw-lobby-1"]

                [routing.contextual.sources]
                "bw-1" = "bed wars"
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();
        Config config = result.config();

        assertTrue(config.routing().contextual().groups().containsKey("bed wars"),
                "Group key with space should survive round-trip");
        assertEquals("bed wars", config.routing().contextual().sources().get("bw-1"));

        // Re-read the written config to verify the key persists
        ConfigLoadResult reRead = manager.load();
        assertTrue(reRead.config().routing().contextual().groups().containsKey("bed wars"),
                "Group key with space should survive write + read round-trip");
    }

    @Test
    void migrationFromV3PreservesSettings() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 3

                [commands]
                primary = "lobby"
                aliases = ["hub", "spawn"]
                permission = "my.custom.perm"
                admin_aliases = ["vn"]
                cooldown_seconds = 5
                reconnect_if_same_server = true

                [routing]
                selection_mode = "round_robin"
                cycle_when_possible = true
                balance_initial_join = true
                default_lobbies = ["lobby-a", "lobby-b"]

                [routing.contextual]
                enabled = true
                fallback_to_default = true

                [routing.contextual.groups]
                bedwars = ["bw-1", "bw-2"]

                [routing.contextual.sources]
                "bw-spawn" = "bedwars"

                [health_checks]
                enabled = true
                timeout_ms = 3000
                cache_seconds = 45

                [messages]
                connecting = "<gold>Sending...</gold>"
                already_connected = "<red>Already there!</red>"
                no_lobby_found = "<red>Full!</red>"
                player_only = "<gray>Players only.</gray>"
                cooldown = "<yellow>Wait!</yellow>"
                reload_success = "<green>Done!</green>"
                reload_failed = "<red>Oops!</red>"

                [update_checker]
                channel = "beta"

                [metrics]
                enabled = false
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();

        assertTrue(result.migrated(), "v3 config should be detected as migrated");
        Config config = result.config();

        // Verify migrated values
        assertEquals("my.custom.perm", config.commands().permission());
        assertEquals(5, config.commands().cooldownSeconds());
        assertEquals(Config.SelectionMode.ROUND_ROBIN, config.routing().selectionMode());
        assertEquals("lobby-a", config.routing().defaultLobbies().get(0).server());
        assertEquals("lobby-b", config.routing().defaultLobbies().get(1).server());
        assertTrue(config.routing().contextual().enabled());
        assertEquals("bedwars", config.routing().contextual().sources().get("bw-spawn"));
        assertEquals(3000, config.healthChecks().timeoutMs());
        assertEquals(45, config.healthChecks().cacheSeconds());
        assertEquals("<gold>Sending...</gold>", config.messages().connecting());
        assertEquals(Config.UpdateChannel.BETA, config.updateChecker().channel());
        assertFalse(config.metrics().enabled());
    }

    @Test
    void lobbyEntryReadsPlainStringFormat() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 4

                [commands]
                aliases = ["hub"]

                [routing]
                default_lobbies = ["lobby-plain-a", "lobby-plain-b"]

                [routing.contextual]
                enabled = false
                fallback_to_default = true

                [health_checks]
                cache_seconds = 30
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();
        Config config = result.config();

        var lobbies = config.routing().defaultLobbies();
        assertEquals(2, lobbies.size());

        assertEquals("lobby-plain-a", lobbies.get(0).server());
        assertEquals(Config.LobbyEntry.UNCAPPED, lobbies.get(0).maxPlayers());
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, lobbies.get(0).weight());

        assertEquals("lobby-plain-b", lobbies.get(1).server());
        assertEquals(Config.LobbyEntry.UNCAPPED, lobbies.get(1).maxPlayers());
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, lobbies.get(1).weight());
    }

    @Test
    void lobbyEntryReadsInlineTableFormat() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 4

                [commands]
                aliases = ["hub"]

                [routing]
                default_lobbies = [{ server = "lobby-table", max_players = 50, weight = 3 }]

                [routing.contextual]
                enabled = false
                fallback_to_default = true

                [health_checks]
                cache_seconds = 30
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();
        Config config = result.config();

        var lobbies = config.routing().defaultLobbies();
        assertEquals(1, lobbies.size());

        assertEquals("lobby-table", lobbies.get(0).server());
        assertEquals(50, lobbies.get(0).maxPlayers());
        assertEquals(3, lobbies.get(0).weight());
    }
}
