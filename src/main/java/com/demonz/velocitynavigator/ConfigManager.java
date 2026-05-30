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

        StringBuilder builder = new StringBuilder();
        builder.append("# VelocityNavigator configuration").append('\n');
        builder.append("# Full docs and support can be found on our official wiki:").append('\n');
        builder.append("# ").append(wiki).append('\n');
        builder.append("# Clean, migration-safe, and ready for premium Velocity networks.").append('\n').append('\n');
        builder.append("# The version marker for this configuration file. Do not change this unless migrating.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#config_version").append('\n');
        builder.append("config_version = ").append(Config.CURRENT_VERSION).append('\n').append('\n');
        builder.append("# Whether to run and log the immediate startup update check.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#notify_on_startup").append('\n');
        builder.append("notify_on_startup = ").append(config.notifyOnStartup()).append('\n');
        builder.append("# Whether admins are notified in-game about available updates when they join.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#notify_admins_on_join").append('\n');
        builder.append("notify_admins_on_join = ").append(config.notifyAdminsOnJoin()).append('\n').append('\n');

        // [startup]
        builder.append("[startup]").append('\n');
        builder.append("# Whether to show the welcome message / upgrade digest on startup.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#startup_welcome_enabled").append('\n');
        builder.append("welcome_enabled = ").append(config.startup().welcomeEnabled()).append('\n');
        builder.append("# The homepage/wiki URL for references and updates.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#startup_wiki_url").append('\n');
        builder.append("wiki_url = ").append(quoted(config.startup().wikiUrl())).append('\n').append('\n');

        // [commands]
        builder.append("[commands]").append('\n');
        builder.append("# The primary command players will use to navigate to the optimal lobby.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Commands#primary").append('\n');
        builder.append("primary = ").append(quoted(config.commands().primary())).append('\n');
        builder.append("# Command aliases that trigger the lobby redirection.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Commands#aliases").append('\n');
        builder.append("aliases = ").append(formatList(config.commands().aliases())).append('\n');
        builder.append("# Permission required to use player commands. Use \"none\" to disable permission checks.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Commands#permission").append('\n');
        if ("velocitynavigator.use".equalsIgnoreCase(config.commands().permission())) {
            builder.append("# NOTE: Default changed to \"none\" in v4.1.0. Review this setting.").append('\n');
        }
        builder.append("permission = ").append(quoted(config.commands().permission())).append('\n');
        builder.append("# Admin command labels. The first one becomes the primary admin command.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Commands#admin_aliases").append('\n');
        builder.append("admin_aliases = ").append(formatList(config.commands().adminAliases())).append('\n');
        builder.append("# Cooldown in seconds between lobby command invocations per player.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Commands#cooldown_seconds").append('\n');
        builder.append("cooldown_seconds = ").append(config.commands().cooldownSeconds()).append('\n');
        builder.append("# Whether /lobby reconnects the player if they are already on the selected lobby.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Commands#reconnect_if_same_server").append('\n');
        builder.append("reconnect_if_same_server = ").append(config.commands().reconnectIfSameServer()).append('\n').append('\n');

        // [routing]
        builder.append("[routing]").append('\n');
        builder.append("# The routing brain mode. Determines how a lobby is selected.").append('\n');
        builder.append("# Available modes: least_players, round_robin, random, power_of_two, weighted_round_robin, least_connections, consistent_hash, latency").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Routing-Algorithms#selection_mode").append('\n');
        builder.append("selection_mode = ").append(quoted(config.routing().selectionMode().configValue())).append('\n');
        builder.append("# Cycle players to a different lobby when they are already on a lobby in the selection pool.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Routing-Algorithms#cycle_when_possible").append('\n');
        builder.append("cycle_when_possible = ").append(config.routing().cycleWhenPossible()).append('\n');
        builder.append("# Whether to balance players across lobbies on their initial join to the proxy.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Routing-Algorithms#balance_initial_join").append('\n');
        builder.append("balance_initial_join = ").append(config.routing().balanceInitialJoin()).append('\n');
        builder.append("# Default lobby servers. Can be simple string server names or inline tables:").append('\n');
        builder.append("#   { server = \"lobby-1\", max_players = 100, weight = 2 }").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#default_lobbies").append('\n');
        builder.append("default_lobbies = ").append(formatLobbyEntryList(config.routing().defaultLobbies())).append('\n');
        builder.append("# Max retry attempts if a connection to a selected lobby server fails.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#max_retries").append('\n');
        builder.append("max_retries = ").append(config.routing().maxRetries()).append('\n');
        builder.append("# Use an interactive chat-based selector menu for Java players on /lobby.").append('\n');
        builder.append("use_chat_menu_for_lobby = ").append(config.routing().useChatMenuForLobby()).append('\n');
        builder.append("# Header text printed at the top of the Java chat selector menu.").append('\n');
        builder.append("chat_menu_header = ").append(quoted(config.routing().chatMenuHeader())).append('\n');
        builder.append("# Format line printed for each lobby candidate in the Java chat menu.").append('\n');
        builder.append("chat_menu_format = ").append(quoted(config.routing().chatMenuFormat())).append('\n');
        builder.append("# Tooltip text shown when hovering over a lobby name in the Java chat menu.").append('\n');
        builder.append("chat_menu_tooltip = ").append(quoted(config.routing().chatMenuTooltip())).append('\n').append('\n');

        // [routing.affinity]
        builder.append("[routing.affinity]").append('\n');
        builder.append("# Session stickiness (affinity) to return players to their previous lobby server.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#affinity_enabled").append('\n');
        builder.append("enabled = ").append(config.routing().affinity().enabled()).append('\n');
        builder.append("# Probability (0.0 to 1.0) that a player stays on their last server on reconnect.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#affinity_stickiness").append('\n');
        builder.append("stickiness = ").append(config.routing().affinity().stickiness()).append('\n').append('\n');

        // [routing.contextual]
        builder.append("[routing.contextual]").append('\n');
        builder.append("# Source-server aware routing.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#contextual_enabled").append('\n');
        builder.append("enabled = ").append(config.routing().contextual().enabled()).append('\n');
        builder.append("# Fall back to default lobbies if no servers in the matched contextual group are available.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#contextual_fallback_to_default").append('\n');
        builder.append("fallback_to_default = ").append(config.routing().contextual().fallbackToDefault()).append('\n').append('\n');

        // [routing.contextual.groups]
        builder.append("[routing.contextual.groups]").append('\n');
        builder.append("# Map contextual group name to list of lobby entries.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#contextual_groups").append('\n');
        for (Map.Entry<String, Config.GroupConfig> entry : config.routing().contextual().groups().entrySet()) {
            if (entry.getValue().mode() != null) {
                builder.append(quoted(entry.getKey())).append(" = { servers = ").append(formatLobbyEntryList(entry.getValue().servers())).append(", mode = ").append(quoted(entry.getValue().mode().configValue())).append(" }").append('\n');
            } else {
                builder.append(quoted(entry.getKey())).append(" = ").append(formatLobbyEntryList(entry.getValue().servers())).append('\n');
            }
        }
        builder.append('\n');

        // [routing.contextual.sources]
        builder.append("[routing.contextual.sources]").append('\n');
        builder.append("# Map source server name to contextual group name.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#contextual_sources").append('\n');
        for (Map.Entry<String, String> entry : config.routing().contextual().sources().entrySet()) {
            builder.append(quoted(entry.getKey())).append(" = ").append(quoted(entry.getValue())).append('\n');
        }
        builder.append('\n');

        // [routing.contextual.fallback_chain]
        if (!config.routing().contextual().fallbackChain().isEmpty()) {
            builder.append("[routing.contextual.fallback_chain]").append('\n');
            builder.append("# Maps a group name to a list of fallback group names in ordered chain.").append('\n');
            builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#contextual_fallback_chain").append('\n');
            for (Map.Entry<String, List<String>> entry : config.routing().contextual().fallbackChain().entrySet()) {
                builder.append(quoted(entry.getKey())).append(" = ").append(formatList(entry.getValue())).append('\n');
            }
            builder.append('\n');
        }

        // [lobby]
        builder.append("[lobby]").append('\n');
        builder.append("# Strategy to apply when no lobby servers are available (disconnect or fallback_server).").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby_no_server_strategy").append('\n');
        builder.append("no_server_strategy = ").append(quoted(config.lobbyFallback().noServerStrategy())).append('\n');
        builder.append("# The disconnect message sent if no lobbies are online and strategy is disconnect.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby_no_server_message").append('\n');
        builder.append("no_server_message = ").append(quoted(config.lobbyFallback().noServerMessage())).append('\n');
        builder.append("# The backup fallback server name if strategy is fallback_server.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby_fallback_server").append('\n');
        builder.append("fallback_server = ").append(quoted(config.lobbyFallback().fallbackServer())).append('\n').append('\n');

        // [health_checks]
        builder.append("[health_checks]").append('\n');
        builder.append("# Enable pinging candidate lobbies before connecting to verify they are active.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks_enabled").append('\n');
        builder.append("enabled = ").append(config.healthChecks().enabled()).append('\n');
        builder.append("# Timeout in milliseconds for server ping health checks.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks_timeout_ms").append('\n');
        builder.append("timeout_ms = ").append(config.healthChecks().timeoutMs()).append('\n');
        builder.append("# How long to cache server health check status in seconds. Set to 0 to disable.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks_cache_seconds").append('\n');
        builder.append("cache_seconds = ").append(config.healthChecks().cacheSeconds()).append('\n').append('\n');

        // [messages]
        builder.append("[messages]").append('\n');
        builder.append("# Custom messages (MiniMessage formatting recommended).").append('\n');
        builder.append("# Placeholders: <server>, <time>, <reason>, <mode>, <player>, <attempt>, <max>").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#messages").append('\n');
        builder.append("connecting = ").append(quoted(config.messages().connecting())).append('\n');
        builder.append("already_connected = ").append(quoted(config.messages().alreadyConnected())).append('\n');
        builder.append("no_lobby_found = ").append(quoted(config.messages().noLobbyFound())).append('\n');
        builder.append("player_only = ").append(quoted(config.messages().playerOnly())).append('\n');
        builder.append("cooldown = ").append(quoted(config.messages().cooldown())).append('\n');
        builder.append("reload_success = ").append(quoted(config.messages().reloadSuccess())).append('\n');
        builder.append("reload_failed = ").append(quoted(config.messages().reloadFailed())).append('\n');
        builder.append("retrying = ").append(quoted(config.messages().retrying())).append('\n');
        builder.append("# Auto-detect, MiniMessage, or Legacy color format mode (auto, minimessage, legacy).").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#messages_formatting").append('\n');
        builder.append("formatting = ").append(quoted(config.messages().formatting())).append('\n');
        builder.append("# Configurable status colors for the dashboard (MiniMessage/RGB support).").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#messages_dashboard_colors").append('\n');
        builder.append("dashboard_healthy = ").append(quoted(config.messages().dashboardHealthy())).append('\n');
        builder.append("dashboard_draining = ").append(quoted(config.messages().dashboardDraining())).append('\n');
        builder.append("dashboard_open = ").append(quoted(config.messages().dashboardOpen())).append('\n');
        builder.append("dashboard_offline = ").append(quoted(config.messages().dashboardOffline())).append('\n').append('\n');

        // [update_checker]
        builder.append("[update_checker]").append('\n');
        builder.append("# Enable periodic update checking tasks.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_enabled").append('\n');
        builder.append("enabled = ").append(config.updateChecker().enabled()).append('\n');
        builder.append("# The update channel to check (release, beta, alpha).").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_channel").append('\n');
        builder.append("channel = ").append(quoted(config.updateChecker().channel().configValue())).append('\n');
        builder.append("# Interval in minutes to perform update checks (minimum 30 minutes).").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_interval").append('\n');
        builder.append("check_interval = ").append(config.updateChecker().checkIntervalMinutes()).append('\n');
        builder.append("# Notify online admins with 'velocitynavigator.admin' permission on join if an update is found.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker_notify_admins").append('\n');
        builder.append("notify_admins = ").append(config.updateChecker().notifyAdmins()).append('\n').append('\n');

        // [bedrock]
        builder.append("[bedrock]").append('\n');
        builder.append("# Enable Bedrock/Geyser player features.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_enabled").append('\n');
        builder.append("enabled = ").append(config.bedrock().enabled()).append('\n');
        builder.append("# Auto-detect Geyser/Floodgate on classpath to enable automatically.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_auto_detect").append('\n');
        builder.append("auto_detect = ").append(config.bedrock().autoDetect()).append('\n');
        builder.append("# Strip advanced MiniMessage formats like gradients and hover/clicks for Bedrock clients.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_strip_advanced_formatting").append('\n');
        builder.append("strip_advanced_formatting = ").append(config.bedrock().stripAdvancedFormatting()).append('\n');
        builder.append("# Use Java UUID mapped by Floodgate for Bedrock player affinity.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock_affinity_use_java_uuid").append('\n');
        builder.append("affinity_use_java_uuid = ").append(config.bedrock().affinityUseJavaUuid()).append('\n');
        builder.append("# Show a native GUI SimpleForm menu for Bedrock players on /lobby connection.").append('\n');
        builder.append("use_gui_for_lobby = ").append(config.bedrock().useGuiForLobby()).append('\n');
        builder.append("# Title text displayed on the Bedrock GUI form selector.").append('\n');
        builder.append("gui_title = ").append(quoted(config.bedrock().guiTitle())).append('\n');
        builder.append("# Description content text printed inside the Bedrock GUI form selector.").append('\n');
        builder.append("gui_content = ").append(quoted(config.bedrock().guiContent())).append('\n');
        builder.append("# Button text formatting for each lobby in the Bedrock GUI form. Placeholders: {server}, {players}").append('\n');
        builder.append("gui_button_format = ").append(quoted(config.bedrock().guiButtonFormat())).append('\n').append('\n');

        // [metrics]
        builder.append("[metrics]").append('\n');
        builder.append("# Enable anonymous bStats metrics collections.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#metrics_enabled").append('\n');
        builder.append("enabled = ").append(config.metrics().enabled()).append('\n').append('\n');

        // [metrics.prometheus]
        builder.append("[metrics.prometheus]").append('\n');
        builder.append("# Enable embedded Prometheus metrics HTTP endpoint. Exposes detailed real-time telemetry.").append('\n');
        builder.append("enabled = ").append(config.metrics().prometheus().enabled()).append('\n');
        builder.append("# Port to listen on for scraping (e.g. http://localhost:9225/metrics).").append('\n');
        builder.append("port = ").append(config.metrics().prometheus().port()).append('\n');
        builder.append("# Bind host IP address. Use '0.0.0.0' to listen on all network interfaces.").append('\n');
        builder.append("bind_host = ").append(quoted(config.metrics().prometheus().bindHost())).append('\n');
        builder.append("# Optional bearer token required in Authorization header. Strongly recommended for non-loopback bind hosts.").append('\n');
        builder.append("bearer_token = ").append(quoted(config.metrics().prometheus().bearerToken())).append('\n').append('\n');

        // [circuit_breaker]
        builder.append("[circuit_breaker]").append('\n');
        builder.append("# Stop routing to lobbies that fail health checks consecutively.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_enabled").append('\n');
        builder.append("enabled = ").append(config.circuitBreaker().enabled()).append('\n');
        builder.append("# Number of consecutive check failures before opening circuit.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_failure_threshold").append('\n');
        builder.append("failure_threshold = ").append(config.circuitBreaker().failureThreshold()).append('\n');
        builder.append("# Seconds to wait in OPEN state before trying a check in HALF_OPEN.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_cooldown_seconds").append('\n');
        builder.append("cooldown_seconds = ").append(config.circuitBreaker().cooldownSeconds()).append('\n');
        builder.append("# Consecutive test successes allowed in HALF_OPEN state before closing circuit.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker_half_open_max_tests").append('\n');
        builder.append("half_open_max_tests = ").append(config.circuitBreaker().halfOpenMaxTests()).append('\n').append('\n');

        // [degradation]
        builder.append("[degradation]").append('\n');
        builder.append("# Fall back to degraded selection if all primary servers are full/offline.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#degradation_enabled").append('\n');
        builder.append("enabled = ").append(config.degradation().enabled()).append('\n');
        builder.append("# Selection mode for degraded routing (random, round_robin, least_players).").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#degradation_mode").append('\n');
        builder.append("mode = ").append(quoted(config.degradation().mode())).append('\n').append('\n');

        // [geo_routing]
        builder.append("[geo_routing]").append('\n');
        builder.append("# Geographic IP-based routing (placeholder stub).").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#geo_routing_enabled").append('\n');
        builder.append("enabled = ").append(config.geoRouting().enabled()).append('\n');
        builder.append("# Path to GeoLite2 City mmdb file.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#geo_routing_database_path").append('\n');
        builder.append("database_path = ").append(quoted(config.geoRouting().databasePath())).append('\n').append('\n');

        // [debug]
        builder.append("[debug]").append('\n');
        builder.append("# Enable verbose routing decision logs in proxy console.").append('\n');
        builder.append("# Wiki: ").append(wiki).append("/Configuration-Guide#debug_verbose_logging").append('\n');
        builder.append("verbose_logging = ").append(config.debug().verboseLogging()).append('\n');

        java.nio.file.Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(tempPath, builder.toString());
        try {
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) {
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
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
