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
        assertEquals(Config.CURRENT_VERSION, result.config().configVersion());
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
        assertEquals("none", result.config().commands().permission());
        assertTrue(result.config().healthChecks().enabled());
        assertEquals(30, result.config().healthChecks().cacheSeconds());
        assertEquals("hub", result.config().commands().aliases().get(0));
        assertEquals(Config.defaults().routing().defaultLobbies(), result.config().routing().defaultLobbies());
    }

    @Test
    void prometheusPortOutsideTcpRangeFallsBackToDefault() {
        Config.PrometheusSettings tooHigh = new Config.PrometheusSettings(true, 70000, "127.0.0.1");
        Config.PrometheusSettings negative = new Config.PrometheusSettings(true, -1, "127.0.0.1");

        assertEquals(9225, tooHigh.port());
        assertEquals(9225, negative.port());
    }

    @Test
    void readsPrometheusBearerToken() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 6

                [metrics.prometheus]
                enabled = true
                port = 9225
                bind_host = "0.0.0.0"
                bearer_token = "secret-token"
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        ConfigLoadResult result = manager.load();

        assertEquals("secret-token", result.config().metrics().prometheus().bearerToken());
    }
}
