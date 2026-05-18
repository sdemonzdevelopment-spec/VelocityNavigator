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
                "power_of_two", "weighted_round_robin", "least_connections", "consistent_hash");
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
        List<Config.LobbyEntry> defaultLobbies = readLobbyEntryList(toml, state, "routing.default_lobbies", "routing.default_lobbies", "settings.lobby_servers", "lobby_servers");

        int maxRetries = readInt(toml, state, "routing.max_retries", defaults.routing().maxRetries(), "routing.max_retries");

        Config.Routing routing = new Config.Routing(
                selectionMode,
                readBoolean(toml, state, "routing.cycle_when_possible", defaults.routing().cycleWhenPossible(), "routing.cycle_when_possible", "cycle_lobbies"),
                readBoolean(toml, state, "routing.balance_initial_join", defaults.routing().balanceInitialJoin(), "routing.balance_initial_join"),
                defaultLobbies,
                contextual,
                maxRetries
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
                readString(toml, state, "messages.retrying", defaults.messages().retrying(), "messages.retrying")
        );

        // AC-04: UpdateCheckerSettings now only has channel
        // Migration: warn about old enabled=false
        Config.UpdateCheckerSettings updateChecker;
        if (sourceVersion < 4) {
            Object oldEnabled = rawValue(toml, "update_checker.enabled");
            if (oldEnabled instanceof Boolean && !(Boolean) oldEnabled) {
                state.warnings.add("update_checker.enabled has been removed in v4. Use /vn updatecheck to manually check. The check always runs on startup if notify_on_startup is true.");
            }
            updateChecker = new Config.UpdateCheckerSettings(
                    Config.UpdateChannel.fromString(readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel"))
            );
        } else {
            updateChecker = new Config.UpdateCheckerSettings(
                    Config.UpdateChannel.fromString(readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel"))
            );
        }

        Config.MetricsSettings metrics = new Config.MetricsSettings(
                readBoolean(toml, state, "metrics.enabled", defaults.metrics().enabled(), "metrics.enabled")
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
                notifyAdminsOnJoin
        );
    }

    private List<Config.LobbyEntry> readLobbyEntryList(Toml toml, ParseState state, String label, String... paths) {
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
            return Config.defaults().routing().defaultLobbies();
        }
        return Config.defaults().routing().defaultLobbies();
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
                String key = sanitizeMapKey(String.valueOf(entry.getKey()));
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
                String key = sanitizeMapKey(String.valueOf(entry.getKey()));
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
        StringBuilder builder = new StringBuilder();
        builder.append("# VelocityNavigator v4 configuration").append('\n');
        builder.append("# Full docs: https://github.com/DemonZDevelopment/VelocityNavigator/blob/main/docs/configuration-guide.md").append('\n');
        builder.append("# Clean, migration-safe, and ready for premium Velocity networks.").append('\n').append('\n');
        builder.append("config_version = ").append(Config.CURRENT_VERSION).append('\n').append('\n');

        builder.append("[commands]").append('\n');
        builder.append("# Primary command players will use.").append('\n');
        builder.append("primary = ").append(quoted(config.commands().primary())).append('\n');
        builder.append("# Aliases for the player command.").append('\n');
        builder.append("aliases = ").append(formatList(config.commands().aliases())).append('\n');
        builder.append("# Permission required for lobby usage. Use \"none\" to allow all players.").append('\n');
        builder.append("permission = ").append(quoted(config.commands().permission())).append('\n');
        builder.append("# Admin command labels. The first one becomes the primary admin command.").append('\n');
        builder.append("admin_aliases = ").append(formatList(config.commands().adminAliases())).append('\n');
        builder.append("# Cooldown in seconds for /lobby.").append('\n');
        builder.append("cooldown_seconds = ").append(config.commands().cooldownSeconds()).append('\n');
        builder.append("# If true, /lobby can reconnect the player to the same lobby.").append('\n');
        builder.append("reconnect_if_same_server = ").append(config.commands().reconnectIfSameServer()).append('\n').append('\n');

        builder.append("[routing]").append('\n');
        builder.append("# The routing brain. Controls how the plugin picks which lobby to send a player to.").append('\n');
        builder.append("#").append('\n');
        builder.append("# Available modes:").append('\n');
        builder.append("#   \"least_players\"          - Sends to the lobby with the fewest players.").append('\n');
        builder.append("#   \"round_robin\"            - Deals players sequentially.").append('\n');
        builder.append("#   \"random\"                 - Picks a lobby at random.").append('\n');
        builder.append("#   \"power_of_two\"           - Picks two random candidates, selects the one with fewer players.").append('\n');
        builder.append("#   \"weighted_round_robin\"   - Round-robin weighted by lobby entry weights.").append('\n');
        builder.append("#   \"least_connections\"      - Uses exponential moving average of player counts.").append('\n');
        builder.append("#   \"consistent_hash\"        - Consistent hashing on player UUID for session affinity.").append('\n');
        builder.append("#").append('\n');
        builder.append("# See docs/routing-algorithms.md for detailed math and scenarios.").append('\n');
        builder.append("selection_mode = ").append(quoted(config.routing().selectionMode().configValue())).append('\n');
        builder.append("# Prefer a different lobby when the player is already in the target pool.").append('\n');
        builder.append("cycle_when_possible = ").append(config.routing().cycleWhenPossible()).append('\n');
        builder.append("# Use the plugin's routing brain for initial proxy connections.").append('\n');
        builder.append("balance_initial_join = ").append(config.routing().balanceInitialJoin()).append('\n');
        builder.append("# Default lobbies used when no contextual group wins.").append('\n');
        builder.append("# Each entry can be a plain server name string or an inline table:").append('\n');
        builder.append("#   { server = \"lobby-1\", max_players = 100, weight = 2 }").append('\n');
        builder.append("default_lobbies = ").append(formatLobbyEntryList(config.routing().defaultLobbies())).append('\n');
        builder.append("# Maximum connection retry attempts when a selected server fails.").append('\n');
        builder.append("max_retries = ").append(config.routing().maxRetries()).append('\n').append('\n');

        builder.append("[routing.contextual]").append('\n');
        builder.append("# Enable source-server aware routing.").append('\n');
        builder.append("enabled = ").append(config.routing().contextual().enabled()).append('\n');
        builder.append("# Fall back to routing.default_lobbies if the contextual group is empty or offline.").append('\n');
        builder.append("fallback_to_default = ").append(config.routing().contextual().fallbackToDefault()).append('\n').append('\n');

        builder.append("[routing.contextual.groups]").append('\n');
        builder.append("# Each group can be a list of lobby entries, or a table with 'servers' and optional 'mode':").append('\n');
        builder.append("#   bedwars = [{ server = \"bw-1\", weight = 2 }, { server = \"bw-2\" }]").append('\n');
        builder.append("#   skywars = { servers = [{ server = \"sw-1\" }], mode = \"random\" }").append('\n');
        for (Map.Entry<String, Config.GroupConfig> entry : config.routing().contextual().groups().entrySet()) {
            if (entry.getValue().mode() != null) {
                builder.append(quoted(entry.getKey())).append(" = { servers = ").append(formatLobbyEntryList(entry.getValue().servers())).append(", mode = ").append(quoted(entry.getValue().mode().configValue())).append(" }").append('\n');
            } else {
                builder.append(quoted(entry.getKey())).append(" = ").append(formatLobbyEntryList(entry.getValue().servers())).append('\n');
            }
        }
        builder.append('\n');

        builder.append("[routing.contextual.sources]").append('\n');
        for (Map.Entry<String, String> entry : config.routing().contextual().sources().entrySet()) {
            builder.append(quoted(entry.getKey())).append(" = ").append(quoted(entry.getValue())).append('\n');
        }
        builder.append('\n');

        if (!config.routing().contextual().fallbackChain().isEmpty()) {
            builder.append("[routing.contextual.fallback_chain]").append('\n');
            builder.append("# Maps a group name to an ordered list of fallback group names.").append('\n');
            for (Map.Entry<String, List<String>> entry : config.routing().contextual().fallbackChain().entrySet()) {
                builder.append(quoted(entry.getKey())).append(" = ").append(formatList(entry.getValue())).append('\n');
            }
            builder.append('\n');
        }

        builder.append("[health_checks]").append('\n');
        builder.append("# Ping candidate lobbies before routing.").append('\n');
        builder.append("enabled = ").append(config.healthChecks().enabled()).append('\n');
        builder.append("# How long a ping can take before the server is considered unavailable.").append('\n');
        builder.append("timeout_ms = ").append(config.healthChecks().timeoutMs()).append('\n');
        builder.append("# Cache server health for this many seconds. Use 0 to disable caching.").append('\n');
        builder.append("cache_seconds = ").append(config.healthChecks().cacheSeconds()).append('\n').append('\n');

        builder.append("[messages]").append('\n');
        builder.append("# Supported placeholders: <server>, <time>, <reason>, <mode>, <player>, <attempt>, <max>").append('\n');
        builder.append("connecting = ").append(quoted(config.messages().connecting())).append('\n');
        builder.append("already_connected = ").append(quoted(config.messages().alreadyConnected())).append('\n');
        builder.append("no_lobby_found = ").append(quoted(config.messages().noLobbyFound())).append('\n');
        builder.append("player_only = ").append(quoted(config.messages().playerOnly())).append('\n');
        builder.append("cooldown = ").append(quoted(config.messages().cooldown())).append('\n');
        builder.append("reload_success = ").append(quoted(config.messages().reloadSuccess())).append('\n');
        builder.append("reload_failed = ").append(quoted(config.messages().reloadFailed())).append('\n');
        builder.append("# Shown when a connection retry is attempted.").append('\n');
        builder.append("retrying = ").append(quoted(config.messages().retrying())).append('\n').append('\n');

        builder.append("[update_checker]").append('\n');
        builder.append("# Update checking is no longer auto-scheduled. Use /vn updatecheck to manually check.").append('\n');
        builder.append("# A startup check is performed if notify_on_startup is true.").append('\n');
        builder.append("# Options: release, beta, alpha").append('\n');
        builder.append("channel = ").append(quoted(config.updateChecker().channel().configValue())).append('\n').append('\n');

        builder.append("# Whether to check for updates once on startup and log to console.").append('\n');
        builder.append("notify_on_startup = ").append(config.notifyOnStartup()).append('\n');
        builder.append("# Whether to notify admins with velocitynavigator.admin permission when they join if an update is available.").append('\n');
        builder.append("notify_admins_on_join = ").append(config.notifyAdminsOnJoin()).append('\n').append('\n');

        builder.append("[metrics]").append('\n');
        builder.append("# bStats support. This can be disabled if your network requires it.").append('\n');
        builder.append("enabled = ").append(config.metrics().enabled()).append('\n').append('\n');

        builder.append("[circuit_breaker]").append('\n');
        builder.append("# Automatically stop routing to servers that fail health checks repeatedly.").append('\n');
        builder.append("enabled = ").append(config.circuitBreaker().enabled()).append('\n');
        builder.append("# Number of consecutive failures before opening the circuit.").append('\n');
        builder.append("failure_threshold = ").append(config.circuitBreaker().failureThreshold()).append('\n');
        builder.append("# Seconds to wait before transitioning from OPEN to HALF_OPEN.").append('\n');
        builder.append("cooldown_seconds = ").append(config.circuitBreaker().cooldownSeconds()).append('\n');
        builder.append("# Number of test requests allowed in HALF_OPEN state.").append('\n');
        builder.append("half_open_max_tests = ").append(config.circuitBreaker().halfOpenMaxTests()).append('\n').append('\n');

        builder.append("[degradation]").append('\n');
        builder.append("# When no routing selection is available, fall back to a degraded selection mode.").append('\n');
        builder.append("enabled = ").append(config.degradation().enabled()).append('\n');
        builder.append("# Degradation mode: random, round_robin, least_players").append('\n');
        builder.append("mode = ").append(quoted(config.degradation().mode())).append('\n').append('\n');

        builder.append("[geo_routing]").append('\n');
        builder.append("# EXPERIMENTAL: Geo-based routing. Requires MaxMind GeoLite2 database (separate download).").append('\n');
        builder.append("# Falls back gracefully when no database is configured.").append('\n');
        builder.append("enabled = ").append(config.geoRouting().enabled()).append('\n');
        builder.append("# Path to GeoLite2-City.mmdb (relative to plugin data directory or absolute).").append('\n');
        builder.append("database_path = ").append(quoted(config.geoRouting().databasePath())).append('\n').append('\n');

        builder.append("[debug]").append('\n');
        builder.append("# Adds extra routing diagnostics to the console.").append('\n');
        builder.append("verbose_logging = ").append(config.debug().verboseLogging()).append('\n');

        Files.writeString(configPath, builder.toString());
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
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class ParseState {
        private final List<String> warnings = new ArrayList<>();
        private boolean normalized;
    }
}
