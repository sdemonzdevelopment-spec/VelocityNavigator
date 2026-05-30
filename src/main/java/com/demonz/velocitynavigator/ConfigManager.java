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

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConfigManager {

    private final Path dataDirectory;
    private final Path configPath;
    private final Logger logger;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.configPath = dataDirectory.resolve("navigator.toml");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Path configPath() {
        return configPath;
    }

    public ConfigLoadResult load() throws IOException {
        Files.createDirectories(dataDirectory);
        if (!Files.exists(configPath)) {
            Config defaults = Config.defaults();
            writeConfig(defaults);
            return new ConfigLoadResult(
                    defaults,
                    List.of("Created navigator.toml with the v4 default layout."),
                    true,
                    false,
                    null,
                    null,
                    false
            );
        }

        Toml toml;
        try {
            toml = new Toml().read(configPath.toFile());
        } catch (RuntimeException exception) {
            Path backupPath = dataDirectory.resolve("navigator.toml.invalid.bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            Config defaults = Config.defaults();
            writeConfig(defaults);
            return new ConfigLoadResult(
                    defaults,
                    List.of("navigator.toml could not be parsed, so the broken file was backed up and defaults were regenerated."),
                    false,
                    false,
                    null,
                    backupPath,
                    true
            );
        }

        ParseState state = new ParseState();
        int sourceVersion = readInt(toml, state, "config_version", 1, "config_version");
        boolean migrated = sourceVersion < Config.CURRENT_VERSION;
        Path backupPath = null;
        if (migrated) {
            backupPath = dataDirectory.resolve("navigator.toml.v" + sourceVersion + ".bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Config config = buildConfig(toml, state, sourceVersion);
        if (migrated || state.normalized) {
            writeConfig(config);
        }
        if (config.routing().defaultLobbies().isEmpty()) {
            state.warnings.add("No valid default lobbies are configured. /lobby will stay online but fail gracefully until servers are added.");
        }
        if (migrated) {
            state.warnings.add(0, "Migrated navigator.toml from v" + sourceVersion + " to v" + Config.CURRENT_VERSION + ".");
        }

        // Run validation on loaded config
        state.warnings.addAll(ConfigValidator.validate(config, toml));

        return new ConfigLoadResult(
                config,
                List.copyOf(state.warnings),
                false,
                migrated,
                migrated ? sourceVersion : null,
                backupPath,
                state.normalized
        );
    }

    public void logWarnings(ConfigLoadResult result) {
        for (String warning : result.warnings()) {
            logger.warn("[VelocityNavigator] {}", warning);
        }
    }

    @SuppressWarnings("unchecked")
    private Config buildConfig(Toml toml, ParseState state, int sourceVersion) {
        Config defaults = Config.defaults();

        String rawSelectionMode = readString(
                toml,
                state,
                "routing.selection_mode",
                defaults.routing().selectionMode().configValue(),
                "routing.selection_mode",
                "settings.selection_mode",
                "selection_mode"
        );
        Config.SelectionMode selectionMode = Config.SelectionMode.fromString(rawSelectionMode);
        if (!selectionMode.configValue().equals(rawSelectionMode.trim().toLowerCase(Locale.ROOT))) {
            state.normalized = true;
        }
        List<String> validModes = List.of("least_players", "random", "round_robin",
                "power_of_two", "weighted_round_robin", "least_connections", "consistent_hash", "latency");
        if (!validModes.contains(rawSelectionMode.trim().toLowerCase(Locale.ROOT))) {
            state.warnings.add("routing.selection_mode was invalid, so it was reset to " + selectionMode.configValue() + ".");
            state.normalized = true;
        }

        Config.Commands commands = new Config.Commands(
                readString(toml, state, "commands.primary", defaults.commands().primary(), "commands.primary"),
                readStringList(toml, state, "commands.aliases", defaults.commands().aliases(), "commands.aliases", "command_aliases"),
                readString(toml, state, "commands.permission", defaults.commands().permission(), "commands.permission"),
                readStringList(toml, state, "commands.admin_aliases", defaults.commands().adminAliases(), "commands.admin_aliases"),
                readInt(toml, state, "commands.cooldown_seconds", defaults.commands().cooldownSeconds(), "commands.cooldown_seconds", "command_cooldown"),
                readBoolean(toml, state, "commands.reconnect_if_same_server", defaults.commands().reconnectIfSameServer(), "commands.reconnect_if_same_server", "reconnect_on_lobby_command")
        );

        // Read lobby entries for contextual groups
        Map<String, Config.GroupConfig> groupConfigs = readGroupConfigMap(toml, state, "routing.contextual.groups", "routing.contextual.groups", "contextual_lobbies.groups");

        // Read fallback chain
        Map<String, List<String>> fallbackChain = readStringListMap(toml, state, "routing.contextual.fallback_chain", "routing.contextual.fallback_chain");

        Config.Contextual contextual = new Config.Contextual(
                readBoolean(toml, state, "routing.contextual.enabled", defaults.routing().contextual().enabled(), "routing.contextual.enabled", "advanced_settings.use_contextual_lobbies"),
                readBoolean(toml, state, "routing.contextual.fallback_to_default", defaults.routing().contextual().fallbackToDefault(), "routing.contextual.fallback_to_default"),
                groupConfigs,
                readStringMap(toml, state, "routing.contextual.sources", "routing.contextual.sources", "contextual_lobbies.mappings"),
                fallbackChain
        );

        // Read default lobbies (support both plain strings and inline tables)
        List<Config.LobbyEntry> defaultLobbies = readLobbyEntryList(
                toml,
                state,
                "routing.default_lobbies",
                defaults.routing().defaultLobbies(),
                "routing.default_lobbies",
                "settings.lobby_servers",
                "lobby_servers"
        );

        int maxRetries = readInt(toml, state, "routing.max_retries", defaults.routing().maxRetries(), "routing.max_retries");

        Config.AffinitySettings affinity = new Config.AffinitySettings(
                readBoolean(toml, state, "routing.affinity.enabled", defaults.routing().affinity().enabled(), "routing.affinity.enabled"),
                readDouble(toml, state, "routing.affinity.stickiness", defaults.routing().affinity().stickiness(), "routing.affinity.stickiness")
        );

        Config.Routing routing = new Config.Routing(
                selectionMode,
                readBoolean(toml, state, "routing.cycle_when_possible", defaults.routing().cycleWhenPossible(), "routing.cycle_when_possible", "cycle_lobbies"),
                readBoolean(toml, state, "routing.balance_initial_join", defaults.routing().balanceInitialJoin(), "routing.balance_initial_join"),
                defaultLobbies,
                contextual,
                maxRetries,
                affinity,
                readBoolean(toml, state, "routing.use_chat_menu_for_lobby", defaults.routing().useChatMenuForLobby(), "routing.use_chat_menu_for_lobby"),
                readString(toml, state, "routing.chat_menu_header", defaults.routing().chatMenuHeader(), "routing.chat_menu_header"),
                readString(toml, state, "routing.chat_menu_format", defaults.routing().chatMenuFormat(), "routing.chat_menu_format"),
                readString(toml, state, "routing.chat_menu_tooltip", defaults.routing().chatMenuTooltip(), "routing.chat_menu_tooltip")
        );

        Config.HealthChecks healthChecks = new Config.HealthChecks(
                readBoolean(toml, state, "health_checks.enabled", defaults.healthChecks().enabled(), "health_checks.enabled", "ping_before_connect"),
                readInt(toml, state, "health_checks.timeout_ms", defaults.healthChecks().timeoutMs(), "health_checks.timeout_ms"),
                readInt(toml, state, "health_checks.cache_seconds", defaults.healthChecks().cacheSeconds(), "health_checks.cache_seconds", "ping_cache_duration")
        );

        Config.Messages messages = new Config.Messages(
                readString(toml, state, "messages.connecting", defaults.messages().connecting(), "messages.connecting"),
                readString(toml, state, "messages.already_connected", defaults.messages().alreadyConnected(), "messages.already_connected"),
                readString(toml, state, "messages.no_lobby_found", defaults.messages().noLobbyFound(), "messages.no_lobby_found"),
                readString(toml, state, "messages.player_only", defaults.messages().playerOnly(), "messages.player_only"),
                readString(toml, state, "messages.cooldown", defaults.messages().cooldown(), "messages.cooldown"),
                readString(toml, state, "messages.reload_success", defaults.messages().reloadSuccess(), "messages.reload_success"),
                readString(toml, state, "messages.reload_failed", defaults.messages().reloadFailed(), "messages.reload_failed"),
                readString(toml, state, "messages.retrying", defaults.messages().retrying(), "messages.retrying"),
                readString(toml, state, "messages.formatting", defaults.messages().formatting(), "messages.formatting"),
                readString(toml, state, "messages.dashboard_healthy", defaults.messages().dashboardHealthy(), "messages.dashboard_healthy"),
                readString(toml, state, "messages.dashboard_draining", defaults.messages().dashboardDraining(), "messages.dashboard_draining"),
                readString(toml, state, "messages.dashboard_open", defaults.messages().dashboardOpen(), "messages.dashboard_open"),
                readString(toml, state, "messages.dashboard_offline", defaults.messages().dashboardOffline(), "messages.dashboard_offline")
        );

        // Load UpdateCheckerSettings with v5 features
        Config.UpdateCheckerSettings updateChecker;
        if (sourceVersion < 5) {
            boolean enabled = true;
            Object oldEnabled = rawValue(toml, "update_checker.enabled");
            if (oldEnabled instanceof Boolean && !(Boolean) oldEnabled) {
                enabled = false;
            }
            Config.UpdateChannel channel = Config.UpdateChannel.fromString(readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel"));
            int checkInterval = readInt(toml, state, "update_checker.check_interval", defaults.updateChecker().checkIntervalMinutes(), "update_checker.check_interval");
            boolean notifyAdmins = readBoolean(toml, state, "update_checker.notify_admins", defaults.updateChecker().notifyAdmins(), "update_checker.notify_admins");
            updateChecker = new Config.UpdateCheckerSettings(enabled, channel, checkInterval, notifyAdmins);
        } else {
            updateChecker = new Config.UpdateCheckerSettings(
                    readBoolean(toml, state, "update_checker.enabled", defaults.updateChecker().enabled(), "update_checker.enabled"),
                    Config.UpdateChannel.fromString(readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel")),
                    readInt(toml, state, "update_checker.check_interval", defaults.updateChecker().checkIntervalMinutes(), "update_checker.check_interval"),
                    readBoolean(toml, state, "update_checker.notify_admins", defaults.updateChecker().notifyAdmins(), "update_checker.notify_admins")
            );
        }

        Config.MetricsSettings metrics = new Config.MetricsSettings(
                readBoolean(toml, state, "metrics.enabled", defaults.metrics().enabled(), "metrics.enabled"),
                new Config.PrometheusSettings(
                        readBoolean(toml, state, "metrics.prometheus.enabled", defaults.metrics().prometheus().enabled(), "metrics.prometheus.enabled"),
                        readInt(toml, state, "metrics.prometheus.port", defaults.metrics().prometheus().port(), "metrics.prometheus.port"),
                        readString(toml, state, "metrics.prometheus.bind_host", defaults.metrics().prometheus().bindHost(), "metrics.prometheus.bind_host"),
                        readString(toml, state, "metrics.prometheus.bearer_token", defaults.metrics().prometheus().bearerToken(), "metrics.prometheus.bearer_token")
                )
        );

        Config.DebugSettings debug = new Config.DebugSettings(
                readBoolean(toml, state, "debug.verbose_logging", defaults.debug().verboseLogging(), "debug.verbose_logging")
        );

        Config.CircuitBreakerSettings circuitBreakerSettings = new Config.CircuitBreakerSettings(
                readBoolean(toml, state, "circuit_breaker.enabled", defaults.circuitBreaker().enabled(), "circuit_breaker.enabled"),
                readInt(toml, state, "circuit_breaker.failure_threshold", defaults.circuitBreaker().failureThreshold(), "circuit_breaker.failure_threshold"),
                readInt(toml, state, "circuit_breaker.cooldown_seconds", defaults.circuitBreaker().cooldownSeconds(), "circuit_breaker.cooldown_seconds"),
                readInt(toml, state, "circuit_breaker.half_open_max_tests", defaults.circuitBreaker().halfOpenMaxTests(), "circuit_breaker.half_open_max_tests")
        );

        Config.DegradationSettings degradationSettings = new Config.DegradationSettings(
                readBoolean(toml, state, "degradation.enabled", defaults.degradation().enabled(), "degradation.enabled"),
                readString(toml, state, "degradation.mode", defaults.degradation().mode(), "degradation.mode")
        );

        Config.GeoRoutingSettings geoRoutingSettings = new Config.GeoRoutingSettings(
                readBoolean(toml, state, "geo_routing.enabled", defaults.geoRouting().enabled(), "geo_routing.enabled"),
                readString(toml, state, "geo_routing.database_path", defaults.geoRouting().databasePath(), "geo_routing.database_path")
        );

        boolean notifyOnStartup = readBoolean(toml, state, "notify_on_startup", defaults.notifyOnStartup(), "notify_on_startup");
        boolean notifyAdminsOnJoin = readBoolean(toml, state, "notify_admins_on_join", defaults.notifyAdminsOnJoin(), "notify_admins_on_join");

        Config.StartupSettings startup = new Config.StartupSettings(
                readBoolean(toml, state, "startup.welcome_enabled", defaults.startup().welcomeEnabled(), "startup.welcome_enabled"),
                readString(toml, state, "startup.wiki_url", defaults.startup().wikiUrl(), "startup.wiki_url")
        );

        Config.LobbyFallbackSettings lobbyFallback = new Config.LobbyFallbackSettings(
                readString(toml, state, "lobby.no_server_strategy", defaults.lobbyFallback().noServerStrategy(), "lobby.no_server_strategy"),
                readString(toml, state, "lobby.no_server_message", defaults.lobbyFallback().noServerMessage(), "lobby.no_server_message"),
                readString(toml, state, "lobby.fallback_server", defaults.lobbyFallback().fallbackServer(), "lobby.fallback_server")
        );

        Config.BedrockSettings bedrock = new Config.BedrockSettings(
                readBoolean(toml, state, "bedrock.enabled", defaults.bedrock().enabled(), "bedrock.enabled"),
                readBoolean(toml, state, "bedrock.auto_detect", defaults.bedrock().autoDetect(), "bedrock.auto_detect"),
                readBoolean(toml, state, "bedrock.strip_advanced_formatting", defaults.bedrock().stripAdvancedFormatting(), "bedrock.strip_advanced_formatting"),
                readBoolean(toml, state, "bedrock.affinity_use_java_uuid", defaults.bedrock().affinityUseJavaUuid(), "bedrock.affinity_use_java_uuid"),
                readBoolean(toml, state, "bedrock.use_gui_for_lobby", defaults.bedrock().useGuiForLobby(), "bedrock.use_gui_for_lobby"),
                readString(toml, state, "bedrock.gui_title", defaults.bedrock().guiTitle(), "bedrock.gui_title"),
                readString(toml, state, "bedrock.gui_content", defaults.bedrock().guiContent(), "bedrock.gui_content"),
                readString(toml, state, "bedrock.gui_button_format", defaults.bedrock().guiButtonFormat(), "bedrock.gui_button_format")
        );

        return new Config(
                Config.CURRENT_VERSION,
                commands,
                routing,
                healthChecks,
                messages,
                updateChecker,
                metrics,
                debug,
                circuitBreakerSettings,
                degradationSettings,
                geoRoutingSettings,
                notifyOnStartup,
                notifyAdminsOnJoin,
                startup,
                lobbyFallback,
                bedrock
        );
    }

    private List<Config.LobbyEntry> readLobbyEntryList(Toml toml, ParseState state, String label, List<Config.LobbyEntry> fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> rawList) {
                List<Config.LobbyEntry> entries = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String text && !text.isBlank()) {
                        entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                    } else if (item instanceof Map<?, ?> map) {
                        entries.add(parseLobbyEntryFromMap(map, label, state));
                    } else {
                        state.warnings.add(label + " contained an unrecognized entry format that was ignored.");
                        state.normalized = true;
                    }
                }
                return entries;
            }
            state.warnings.add(label + " expected a list. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Config.LobbyEntry parseLobbyEntryFromMap(Map<?, ?> map, String label, ParseState state) {
        String server = "";
        int maxPlayers = Config.LobbyEntry.UNCAPPED;
        int weight = Config.LobbyEntry.DEFAULT_WEIGHT;

        Object serverObj = map.get("server");
        if (serverObj instanceof String s && !s.isBlank()) {
            server = s.trim();
        } else {
            state.warnings.add(label + " contained a lobby entry without a valid 'server' field.");
            state.normalized = true;
        }

        Object maxObj = map.get("max_players");
        if (maxObj instanceof Number n) {
            maxPlayers = n.intValue();
        }

        Object weightObj = map.get("weight");
        if (weightObj instanceof Number n) {
            weight = n.intValue();
        }

        return new Config.LobbyEntry(server, maxPlayers, weight);
    }

    private Map<String, Config.GroupConfig> readGroupConfigMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Config.GroupConfig> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (key.isBlank()) {
                    continue;
                }

                Object groupValue = entry.getValue();

                // Old format: list of strings
                if (groupValue instanceof List<?> rawList) {
                    List<Config.LobbyEntry> entries = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof String text && !text.isBlank()) {
                            entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                        } else if (item instanceof Map<?, ?> map) {
                            entries.add(parseLobbyEntryFromMap(map, label + "." + key, state));
                        }
                    }
                    if (!entries.isEmpty()) {
                        result.put(key, new Config.GroupConfig(entries, null));
                    }
                    continue;
                }

                // New format: table with servers and mode
                if (groupValue instanceof Map<?, ?> groupMap) {
                    List<Config.LobbyEntry> entries = new ArrayList<>();
                    Object serversObj = groupMap.get("servers");
                    if (serversObj instanceof List<?> serversList) {
                        for (Object item : serversList) {
                            if (item instanceof String text && !text.isBlank()) {
                                entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                            } else if (item instanceof Map<?, ?> map) {
                                entries.add(parseLobbyEntryFromMap(map, label + "." + key, state));
                            }
                        }
                    }

                    Config.SelectionMode mode = null;
                    Object modeObj = groupMap.get("mode");
                    if (modeObj instanceof String modeStr && !modeStr.isBlank()) {
                        mode = Config.SelectionMode.fromString(modeStr);
                    }

                    if (!entries.isEmpty()) {
                        result.put(key, new Config.GroupConfig(entries, mode));
                    }
                    continue;
                }

                state.warnings.add(label + "." + key + " expected a list or table and was ignored.");
                state.normalized = true;
            }
            return result;
        }
        return Map.of();
    }

    private String readString(Toml toml, ParseState state, String label, String fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof String text) {
                return text;
            }
            state.warnings.add(label + " expected a string. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private boolean readBoolean(Toml toml, ParseState state, String label, boolean fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            state.warnings.add(label + " expected true/false. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private int readInt(Toml toml, ParseState state, String label, int fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            state.warnings.add(label + " expected a number. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private double readDouble(Toml toml, ParseState state, String label, double fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            state.warnings.add(label + " expected a number. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private List<String> readStringList(Toml toml, ParseState state, String label, List<String> fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> rawList) {
                List<String> cleaned = new ArrayList<>();
                for (Object entry : rawList) {
                    if (entry instanceof String text && !text.isBlank()) {
                        cleaned.add(text.trim());
                    } else {
                        state.warnings.add(label + " contained a non-string entry that was ignored.");
                        state.normalized = true;
                    }
                }
                return cleaned;
            }
            state.warnings.add(label + " expected a list of strings. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private Map<String, List<String>> readStringListMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (!(entry.getValue() instanceof List<?> rawList)) {
                    state.warnings.add(label + "." + key + " expected a list of strings and was ignored.");
                    state.normalized = true;
                    continue;
                }
                List<String> cleaned = new ArrayList<>();
                for (Object rawItem : rawList) {
                    if (rawItem instanceof String text && !text.isBlank()) {
                        cleaned.add(text.trim());
                    } else {
                        state.warnings.add(label + "." + key + " contained a non-string entry that was ignored.");
                        state.normalized = true;
                    }
                }
                values.put(key, cleaned);
            }
            return values;
        }
        return Map.of();
    }

    /**
     * Reads a string map from TOML, lowercasing keys so that they match
     * the lowercase lookups in RoutePlanner (e.g. contextual sources).
     */
    private Map<String, String> readStringMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (entry.getValue() instanceof String text && !text.isBlank()) {
                    values.put(key, text.trim().toLowerCase(Locale.ROOT));
                } else {
                    state.warnings.add(label + "." + key + " expected a string and was ignored.");
                    state.normalized = true;
                }
            }
            return values;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Object rawValue(Toml toml, String path) {
        Object current = toml.toMap();
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String sanitizeMapKey(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void writeConfig(Config config) throws IOException {
        String wiki = config.startup() != null ? config.startup().wikiUrl() : "https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki";
        if (wiki.endsWith("/")) {
            wiki = wiki.substring(0, wiki.length() - 1);
        }

        StringBuilder b = new StringBuilder();

        // ── Header ──────────────────────────────────────────────────────────
        b.append("# ╔══════════════════════════════════════════════════════════════════╗\n");
        b.append("# ║           VelocityNavigator — Configuration File               ║\n");
        b.append("# ║     Premium lobby navigation for Velocity proxy networks        ║\n");
        b.append("# ╠══════════════════════════════════════════════════════════════════╣\n");
        b.append("# ║  Docs & Wiki : ").append(padRight(wiki, 49)).append("║\n");
        b.append("# ║  Support     : https://discord.gg/demonz                        ║\n");
        b.append("# ║  bStats      : https://bstats.org/plugin/velocity/Velocity%20Navigator/28341 ║\n");
        b.append("# ╚══════════════════════════════════════════════════════════════════╝\n");
        b.append("#\n");
        b.append("# This file is auto-generated and self-documenting. Every key has\n");
        b.append("# a description and a link to the relevant wiki section. Feel free\n");
        b.append("# to edit it — your changes are preserved across upgrades.\n");
        b.append("#\n");
        b.append("# Tip: Run /vn reload after saving changes. No proxy restart needed!\n");
        b.append("\n");

        // ── Config version ──────────────────────────────────────────────────
        b.append("# Internal config schema version. Do NOT change this manually.\n");
        b.append("# VelocityNavigator uses it to auto-migrate your settings on upgrade.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#config_version\n");
        b.append("config_version = ").append(Config.CURRENT_VERSION).append("\n\n");

        // ── Global notifications ────────────────────────────────────────────
        b.append("# Check for plugin updates when the proxy starts.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#notify_on_startup\n");
        b.append("notify_on_startup = ").append(config.notifyOnStartup()).append("\n\n");
        b.append("# Show an in-game update notification to admins when they join.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#notify_admins_on_join\n");
        b.append("notify_admins_on_join = ").append(config.notifyAdminsOnJoin()).append("\n\n");

        // ── [startup] ───────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  STARTUP — First-run welcome & upgrade digest                  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[startup]\n\n");
        b.append("# Display a welcome banner on fresh installs and a changelog digest\n");
        b.append("# when upgrading from a previous version.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#startup_welcome_enabled\n");
        b.append("welcome_enabled = ").append(config.startup().welcomeEnabled()).append("\n\n");
        b.append("# The wiki homepage URL used in console messages and config comments.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#startup_wiki_url\n");
        b.append("wiki_url = ").append(quoted(config.startup().wikiUrl())).append("\n\n");

        // ── [commands] ──────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  COMMANDS — Player-facing & admin commands                      │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[commands]\n\n");
        b.append("# Primary lobby command name. Players type /<primary> to navigate.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#primary\n");
        b.append("primary = ").append(quoted(config.commands().primary())).append("\n\n");
        b.append("# Additional command aliases (e.g. /hub, /spawn) that behave like /<primary>.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#aliases\n");
        b.append("aliases = ").append(formatList(config.commands().aliases())).append("\n\n");
        b.append("# Permission node required to use the lobby command.\n");
        b.append("# Set to \"none\" for no permission check (recommended for public servers).\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#permission\n");
        if ("velocitynavigator.use".equalsIgnoreCase(config.commands().permission())) {
            b.append("# NOTE: Default changed to \"none\" in v4.1.0. Review this setting.\n");
        }
        b.append("permission = ").append(quoted(config.commands().permission())).append("\n\n");
        b.append("# Admin command labels. First entry is primary (e.g. /velocitynavigator).\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#admin_aliases\n");
        b.append("admin_aliases = ").append(formatList(config.commands().adminAliases())).append("\n\n");
        b.append("# Cooldown between lobby commands per player (seconds). 0 = disabled.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#cooldown_seconds\n");
        b.append("cooldown_seconds = ").append(config.commands().cooldownSeconds()).append("\n\n");
        b.append("# Allow /lobby to reconnect the player even if they're already on the\n");
        b.append("# selected server. Set to false to show \"already connected\" instead.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#reconnect_if_same_server\n");
        b.append("reconnect_if_same_server = ").append(config.commands().reconnectIfSameServer()).append("\n\n");

        // ── [routing] ───────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  ROUTING — The brain of VelocityNavigator                      │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  How players are matched to lobby servers. Choose an algorithm, │\n");
        b.append("# │  list your lobbies, and tune retry/affinity behavior.           │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[routing]\n\n");
        b.append("# Routing algorithm that decides which lobby a player is sent to.\n");
        b.append("#\n");
        b.append("#   least_players       — Fewest players (default, great all-rounder)\n");
        b.append("#   round_robin         — Even rotation across lobbies\n");
        b.append("#   random              — Random pick (simple, fast)\n");
        b.append("#   power_of_two        — Pick 2 random, choose the lighter one (O(1) optimal)\n");
        b.append("#   weighted_round_robin — Proportional traffic based on server weights\n");
        b.append("#   least_connections   — Lowest EMA connection load\n");
        b.append("#   consistent_hash     — Deterministic player-to-server mapping\n");
        b.append("#   latency             — Lowest health-check ping time\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Routing-Algorithms\n");
        b.append("selection_mode = ").append(quoted(config.routing().selectionMode().configValue())).append("\n\n");
        b.append("# Prefer sending players to a DIFFERENT lobby than the one they are on.\n");
        b.append("# Only applies when multiple candidates are available.\n");
        b.append("# Wiki: ").append(wiki).append("/Routing-Algorithms#cycle_when_possible\n");
        b.append("cycle_when_possible = ").append(config.routing().cycleWhenPossible()).append("\n\n");
        b.append("# Load-balance players the moment they connect to the proxy, not only\n");
        b.append("# when they type /lobby. Highly recommended for large networks.\n");
        b.append("# Wiki: ").append(wiki).append("/Initial-Join-Balancing\n");
        b.append("balance_initial_join = ").append(config.routing().balanceInitialJoin()).append("\n\n");
        b.append("# Your lobby servers. Entries can be plain strings or inline tables:\n");
        b.append("#\n");
        b.append("#   \"lobby-1\"                                          — simple\n");
        b.append("#   { server = \"lobby-1\", max_players = 100 }          — with cap\n");
        b.append("#   { server = \"lobby-1\", max_players = 100, weight = 2 } — with cap + weight\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#default_lobbies\n");
        b.append("default_lobbies = ").append(formatLobbyEntryList(config.routing().defaultLobbies())).append("\n\n");
        b.append("# How many times to retry a different lobby if the first connection fails.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#max_retries\n");
        b.append("max_retries = ").append(config.routing().maxRetries()).append("\n\n");
        b.append("# Show an interactive click-to-connect chat menu instead of auto-routing.\n");
        b.append("# Players can also type /lobby menu to trigger it manually.\n");
        b.append("use_chat_menu_for_lobby = ").append(config.routing().useChatMenuForLobby()).append("\n\n");
        b.append("# Header line printed above the chat menu (MiniMessage supported).\n");
        b.append("chat_menu_header = ").append(quoted(config.routing().chatMenuHeader())).append("\n\n");
        b.append("# Line format for each lobby entry in the chat menu.\n");
        b.append("# Placeholders: {server}, {players}, {max_players}, {status}, {status_color}, {ping}\n");
        b.append("chat_menu_format = ").append(quoted(config.routing().chatMenuFormat())).append("\n\n");
        b.append("# Hover tooltip shown over each lobby entry in the chat menu.\n");
        b.append("# Placeholders: {server}, {players}, {max_players}, {status}, {status_color}, {ping}\n");
        b.append("chat_menu_tooltip = ").append(quoted(config.routing().chatMenuTooltip())).append("\n\n");

        // ── [routing.affinity] ──────────────────────────────────────────────
        b.append("# ── Player Affinity (Sticky Sessions) ──────────────────────────────\n");
        b.append("[routing.affinity]\n\n");
        b.append("# Remember which lobby a player was on and try to send them back.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#affinity_enabled\n");
        b.append("enabled = ").append(config.routing().affinity().enabled()).append("\n\n");
        b.append("# Probability (0.0–1.0) of returning the player to their last lobby.\n");
        b.append("# 0.0 = never sticky, 1.0 = always sticky.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#affinity_stickiness\n");
        b.append("stickiness = ").append(config.routing().affinity().stickiness()).append("\n\n");

        // ── [routing.contextual] ────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  CONTEXTUAL ROUTING — Game-mode-aware lobby selection           │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  Route players to specific lobbies depending on which server    │\n");
        b.append("# │  they are leaving. Great for game modes with dedicated lobbies.  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[routing.contextual]\n\n");
        b.append("# Enable source-server-aware routing.\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide\n");
        b.append("enabled = ").append(config.routing().contextual().enabled()).append("\n\n");
        b.append("# If no servers in the matched group are online, fall back to default lobbies.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#contextual_fallback_to_default\n");
        b.append("fallback_to_default = ").append(config.routing().contextual().fallbackToDefault()).append("\n\n");

        // [routing.contextual.groups]
        b.append("# ── Contextual Groups ───────────────────────────────────────────────\n");
        b.append("# Define named groups of lobby servers. Each group can optionally\n");
        b.append("# override the global selection_mode with its own \"mode\" field.\n");
        b.append("#\n");
        b.append("# Example:\n");
        b.append("#   [routing.contextual.groups]\n");
        b.append("#   \"bedwars\"  = { servers = [\"bw-lobby-1\", \"bw-lobby-2\"], mode = \"round_robin\" }\n");
        b.append("#   \"skyblock\" = [\"sb-lobby-1\"]\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide#groups\n");
        b.append("[routing.contextual.groups]\n");
        for (Map.Entry<String, Config.GroupConfig> entry : config.routing().contextual().groups().entrySet()) {
            if (entry.getValue().mode() != null) {
                b.append(quoted(entry.getKey())).append(" = { servers = ").append(formatLobbyEntryList(entry.getValue().servers())).append(", mode = ").append(quoted(entry.getValue().mode().configValue())).append(" }\n");
            } else {
                b.append(quoted(entry.getKey())).append(" = ").append(formatLobbyEntryList(entry.getValue().servers())).append("\n");
            }
        }
        b.append("\n");

        // [routing.contextual.sources]
        b.append("# ── Source Mappings ────────────────────────────────────────────────\n");
        b.append("# Map a source server name → contextual group name.\n");
        b.append("# When a player leaves \"bedwars-1\", they'll be routed to the \"bedwars\" group.\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide#sources\n");
        b.append("[routing.contextual.sources]\n");
        for (Map.Entry<String, String> entry : config.routing().contextual().sources().entrySet()) {
            b.append(quoted(entry.getKey())).append(" = ").append(quoted(entry.getValue())).append("\n");
        }
        b.append("\n");

        // [routing.contextual.fallback_chain]
        if (!config.routing().contextual().fallbackChain().isEmpty()) {
            b.append("# ── Fallback Chain ────────────────────────────────────────────────\n");
            b.append("# Ordered list of fallback groups to try before using default lobbies.\n");
            b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#contextual_fallback_chain\n");
            b.append("[routing.contextual.fallback_chain]\n");
            for (Map.Entry<String, List<String>> entry : config.routing().contextual().fallbackChain().entrySet()) {
                b.append(quoted(entry.getKey())).append(" = ").append(formatList(entry.getValue())).append("\n");
            }
            b.append("\n");
        }

        // ── [lobby] ─────────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  LOBBY FALLBACK — What happens when no lobbies are available    │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[lobby]\n\n");
        b.append("# What to do when every lobby is offline or circuit-broken.\n");
        b.append("#   \"disconnect\"      — Disconnect the player with a message\n");
        b.append("#   \"fallback_server\" — Route to a backup fallback server instead\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby_no_server_strategy\n");
        b.append("no_server_strategy = ").append(quoted(config.lobbyFallback().noServerStrategy())).append("\n\n");
        b.append("# Disconnect message shown when strategy = \"disconnect\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby_no_server_message\n");
        b.append("no_server_message = ").append(quoted(config.lobbyFallback().noServerMessage())).append("\n\n");
        b.append("# Backup server name used when strategy = \"fallback_server\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby_fallback_server\n");
        b.append("fallback_server = ").append(quoted(config.lobbyFallback().fallbackServer())).append("\n\n");

        // ── [health_checks] ─────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  HEALTH CHECKS — Verify servers are alive before routing        │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[health_checks]\n\n");
        b.append("# Ping candidate lobbies before sending a player. Prevents routing\n");
        b.append("# to offline servers. Strongly recommended.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks_enabled\n");
        b.append("enabled = ").append(config.healthChecks().enabled()).append("\n\n");
        b.append("# Timeout in milliseconds before a health-check ping is considered failed.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks_timeout_ms\n");
        b.append("timeout_ms = ").append(config.healthChecks().timeoutMs()).append("\n\n");
        b.append("# Cache health results for this many seconds. Reduces network load.\n");
        b.append("# Set to 0 to ping on every request (not recommended for large networks).\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks_cache_seconds\n");
        b.append("cache_seconds = ").append(config.healthChecks().cacheSeconds()).append("\n\n");

        // ── [circuit_breaker] ───────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  CIRCUIT BREAKER — Automatic failure detection & recovery       │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  State machine: CLOSED → OPEN → HALF_OPEN → CLOSED             │\n");
        b.append("# │  Unhealthy servers are skipped until they prove they've recovered. │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[circuit_breaker]\n\n");
        b.append("# Enable the circuit breaker. When enabled, servers that fail\n");
        b.append("# health checks repeatedly are temporarily removed from routing.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_enabled\n");
        b.append("enabled = ").append(config.circuitBreaker().enabled()).append("\n\n");
        b.append("# Number of consecutive failures before the circuit trips OPEN.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_failure_threshold\n");
        b.append("failure_threshold = ").append(config.circuitBreaker().failureThreshold()).append("\n\n");
        b.append("# Seconds to wait in OPEN state before allowing a test in HALF_OPEN.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_cooldown_seconds\n");
        b.append("cooldown_seconds = ").append(config.circuitBreaker().cooldownSeconds()).append("\n\n");
        b.append("# Successful test connections needed in HALF_OPEN to close the circuit.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_half_open_max_tests\n");
        b.append("half_open_max_tests = ").append(config.circuitBreaker().halfOpenMaxTests()).append("\n\n");

        // ── [messages] ──────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  MESSAGES — All player-facing text (MiniMessage format)         │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  Available placeholders:                                        │\n");
        b.append("# │    <server>  <player>  <time>  <reason>  <mode>                 │\n");
        b.append("# │    <attempt>  <max>                                             │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[messages]\n\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#messages\n");
        b.append("connecting = ").append(quoted(config.messages().connecting())).append("\n");
        b.append("already_connected = ").append(quoted(config.messages().alreadyConnected())).append("\n");
        b.append("no_lobby_found = ").append(quoted(config.messages().noLobbyFound())).append("\n");
        b.append("player_only = ").append(quoted(config.messages().playerOnly())).append("\n");
        b.append("cooldown = ").append(quoted(config.messages().cooldown())).append("\n");
        b.append("reload_success = ").append(quoted(config.messages().reloadSuccess())).append("\n");
        b.append("reload_failed = ").append(quoted(config.messages().reloadFailed())).append("\n");
        b.append("retrying = ").append(quoted(config.messages().retrying())).append("\n\n");
        b.append("# Color code handling: \"auto\" (detect & convert), \"minimessage\", or \"legacy\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#messages_formatting\n");
        b.append("formatting = ").append(quoted(config.messages().formatting())).append("\n\n");
        b.append("# Status tag colors for the /vn servers dashboard (MiniMessage/hex).\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#messages_dashboard_colors\n");
        b.append("dashboard_healthy = ").append(quoted(config.messages().dashboardHealthy())).append("\n");
        b.append("dashboard_draining = ").append(quoted(config.messages().dashboardDraining())).append("\n");
        b.append("dashboard_open = ").append(quoted(config.messages().dashboardOpen())).append("\n");
        b.append("dashboard_offline = ").append(quoted(config.messages().dashboardOffline())).append("\n\n");

        // ── [update_checker] ────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  UPDATE CHECKER — Automatic Modrinth version checking           │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[update_checker]\n\n");
        b.append("# Enable periodic update checking via the Modrinth API.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_enabled\n");
        b.append("enabled = ").append(config.updateChecker().enabled()).append("\n\n");
        b.append("# Which release channel to follow: \"release\", \"beta\", or \"alpha\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_channel\n");
        b.append("channel = ").append(quoted(config.updateChecker().channel().configValue())).append("\n\n");
        b.append("# Minutes between update checks (minimum: 30). Backoff is applied\n");
        b.append("# automatically if the API returns 429 Too Many Requests.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_interval\n");
        b.append("check_interval = ").append(config.updateChecker().checkIntervalMinutes()).append("\n\n");
        b.append("# Notify online admins (velocitynavigator.admin) when they join.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_notify_admins\n");
        b.append("notify_admins = ").append(config.updateChecker().notifyAdmins()).append("\n\n");

        // ── [bedrock] ───────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  BEDROCK — Geyser/Floodgate integration for Bedrock players    │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[bedrock]\n\n");
        b.append("# Manually enable Bedrock support. When false, auto_detect takes over.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_enabled\n");
        b.append("enabled = ").append(config.bedrock().enabled()).append("\n\n");
        b.append("# Automatically enable Bedrock features if Geyser/Floodgate is detected.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_auto_detect\n");
        b.append("auto_detect = ").append(config.bedrock().autoDetect()).append("\n\n");
        b.append("# Strip gradients, hover events, and click actions for Bedrock clients.\n");
        b.append("# Bedrock doesn't support advanced MiniMessage formatting natively.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_strip_advanced_formatting\n");
        b.append("strip_advanced_formatting = ").append(config.bedrock().stripAdvancedFormatting()).append("\n\n");
        b.append("# Use the Java UUID (mapped by Floodgate) for player affinity tracking.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_affinity_use_java_uuid\n");
        b.append("affinity_use_java_uuid = ").append(config.bedrock().affinityUseJavaUuid()).append("\n\n");
        b.append("# Show a native Bedrock SimpleForm GUI instead of chat-based menu.\n");
        b.append("# Requires Floodgate to be installed alongside Geyser.\n");
        b.append("use_gui_for_lobby = ").append(config.bedrock().useGuiForLobby()).append("\n\n");
        b.append("# Bedrock GUI form customization.\n");
        b.append("gui_title = ").append(quoted(config.bedrock().guiTitle())).append("\n");
        b.append("gui_content = ").append(quoted(config.bedrock().guiContent())).append("\n\n");
        b.append("# Button text for each lobby in the Bedrock form. Placeholders: {server}, {players}\n");
        b.append("gui_button_format = ").append(quoted(config.bedrock().guiButtonFormat())).append("\n\n");

        // ── [metrics] ───────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  METRICS — Telemetry & monitoring                               │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[metrics]\n\n");
        b.append("# Enable anonymous bStats telemetry. Helps us understand plugin usage.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#metrics_enabled\n");
        b.append("enabled = ").append(config.metrics().enabled()).append("\n\n");

        // [metrics.prometheus]
        b.append("# ── Prometheus Exporter ─────────────────────────────────────────────\n");
        b.append("# Exposes real-time metrics at http://<bind_host>:<port>/metrics\n");
        b.append("# Compatible with Prometheus, Grafana, and any OpenMetrics scraper.\n");
        b.append("# Tip: Run /vn setup grafana to generate a ready-made dashboard!\n");
        b.append("[metrics.prometheus]\n");
        b.append("enabled = ").append(config.metrics().prometheus().enabled()).append("\n");
        b.append("port = ").append(config.metrics().prometheus().port()).append("\n");
        b.append("bind_host = ").append(quoted(config.metrics().prometheus().bindHost())).append("\n\n");
        b.append("# Optional bearer token for authentication. Strongly recommended if\n");
        b.append("# bind_host is not 127.0.0.1/localhost. Leave empty to disable.\n");
        b.append("bearer_token = ").append(quoted(config.metrics().prometheus().bearerToken())).append("\n\n");

        // ── [degradation] ───────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  DEGRADATION — Graceful fallback when everything is down        │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[degradation]\n\n");
        b.append("# When all health checks fail, use this fallback routing mode instead\n");
        b.append("# of showing \"no lobby found\". Useful for keeping players connected.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#degradation_enabled\n");
        b.append("enabled = ").append(config.degradation().enabled()).append("\n\n");
        b.append("# Fallback algorithm: \"random\", \"round_robin\", or \"least_players\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#degradation_mode\n");
        b.append("mode = ").append(quoted(config.degradation().mode())).append("\n\n");

        // ── [geo_routing] ───────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  GEO ROUTING — Location-based server selection (experimental)   │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[geo_routing]\n\n");
        b.append("# Enable geographic IP-based routing using MaxMind GeoLite2.\n");
        b.append("# This feature is experimental and may not affect routing in this build.\n");
        b.append("# Wiki: ").append(wiki).append("/GeoIP-Database-Setup\n");
        b.append("enabled = ").append(config.geoRouting().enabled()).append("\n\n");
        b.append("# Absolute path to the GeoLite2-Country.mmdb or GeoLite2-City.mmdb file.\n");
        b.append("# Wiki: ").append(wiki).append("/GeoIP-Database-Setup#database_path\n");
        b.append("database_path = ").append(quoted(config.geoRouting().databasePath())).append("\n\n");

        // ── [debug] ─────────────────────────────────────────────────────────
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  DEBUG — Diagnostic logging                                     │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[debug]\n\n");
        b.append("# Print detailed routing decisions, health check results, and cache\n");
        b.append("# events to the proxy console. Useful for troubleshooting.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#debug_verbose_logging\n");
        b.append("verbose_logging = ").append(config.debug().verboseLogging()).append("\n\n");

        // ── Footer ──────────────────────────────────────────────────────────
        b.append("# ╔══════════════════════════════════════════════════════════════════╗\n");
        b.append("# ║  Thank you for using VelocityNavigator!                        ║\n");
        b.append("# ║  Built with ❤ by DemonZ Development                            ║\n");
        b.append("# ║                                                                 ║\n");
        b.append("# ║  Questions? → https://discord.gg/demonz                         ║\n");
        b.append("# ║  Found a bug? → https://github.com/sdemonzdevelopment-spec/     ║\n");
        b.append("# ║                 VelocityNavigator/issues                        ║\n");
        b.append("# ╚══════════════════════════════════════════════════════════════════╝\n");

        java.nio.file.Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(tempPath, b.toString());
        try {
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) {
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String padRight(String text, int length) {
        if (text == null) text = "";
        if (text.length() >= length) return text;
        return text + " ".repeat(length - text.length());
    }

    private String formatLobbyEntryList(List<Config.LobbyEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "[]";
        }
        List<String> items = new ArrayList<>();
        for (Config.LobbyEntry entry : entries) {
            boolean needsTable = entry.maxPlayers() != Config.LobbyEntry.UNCAPPED || entry.weight() != Config.LobbyEntry.DEFAULT_WEIGHT;
            if (needsTable) {
                StringBuilder sb = new StringBuilder("{ server = ").append(quoted(entry.server()));
                if (entry.maxPlayers() != Config.LobbyEntry.UNCAPPED) {
                    sb.append(", max_players = ").append(entry.maxPlayers());
                }
                if (entry.weight() != Config.LobbyEntry.DEFAULT_WEIGHT) {
                    sb.append(", weight = ").append(entry.weight());
                }
                sb.append(" }");
                items.add(sb.toString());
            } else {
                items.add(quoted(entry.server()));
            }
        }
        return "[" + String.join(", ", items) + "]";
    }

    private String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add(quoted(value));
        }
        return "[" + String.join(", ", escaped) + "]";
    }

    private String quoted(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static final class ParseState {
        private final List<String> warnings = new ArrayList<>();
        private boolean normalized;
    }
}
