package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDefaultConfigWhenMissing() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));

        ConfigLoadResult result = manager.load();

        assertTrue(result.createdDefault());
        assertEquals(3, result.config().configVersion());
        assertTrue(Files.exists(tempDir.resolve("navigator.toml")));
    }

    @Test
    void migratesLegacyFlatConfigAndCreatesBackup() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 2
                lobby_servers = ["lobby-a", "lobby-b"]
                selection_mode = "ROUND_ROBIN"
                command_aliases = ["hub", "spawn", "lobby"]
                reconnect_on_lobby_command = true
                command_cooldown = 9
                ping_before_connect = false
                ping_cache_duration = 10

                [advanced_settings]
                use_contextual_lobbies = true

                [contextual_lobbies.groups]
                bedwars = ["bw-lobby-1", "bw-lobby-2"]

                [contextual_lobbies.mappings]
                "bedwars-1" = "bedwars"
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        ConfigLoadResult result = manager.load();

        assertTrue(result.migrated());
        assertTrue(Files.exists(tempDir.resolve("navigator.toml.v2.bak")));
        assertEquals(Config.SelectionMode.ROUND_ROBIN, result.config().routing().selectionMode());
        assertTrue(result.config().commands().reconnectIfSameServer());
        assertTrue(result.config().routing().contextual().enabled());
        assertEquals("bedwars", result.config().routing().contextual().sources().get("bedwars-1"));
    }

    @Test
    void fallsBackFieldByFieldWhenValuesAreInvalid() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 3

                [commands]
                aliases = ["hub"]
                permission = 4
                cooldown_seconds = "fast"

                [routing]
                selection_mode = "chaos"
                default_lobbies = "not-a-list"

                [health_checks]
                enabled = "sometimes"
                timeout_ms = "slow"
                cache_seconds = 30
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        ConfigLoadResult result = manager.load();

        assertFalse(result.warnings().isEmpty());
        assertEquals(Config.SelectionMode.LEAST_PLAYERS, result.config().routing().selectionMode());
        assertEquals(3, result.config().commands().cooldownSeconds());
        assertEquals("velocitynavigator.use", result.config().commands().permission());
        assertTrue(result.config().healthChecks().enabled());
        assertEquals(30, result.config().healthChecks().cacheSeconds());
        assertEquals("hub", result.config().commands().aliases().get(0));
        assertEquals(Config.defaults().routing().defaultLobbies(), result.config().routing().defaultLobbies());
    }
}
