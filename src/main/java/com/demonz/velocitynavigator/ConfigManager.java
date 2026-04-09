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
                    List.of("Created navigator.toml with the v3 default layout."),
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

        Config config = buildConfig(toml, state);
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

    private Config buildConfig(Toml toml, ParseState state) {
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
        if (!List.of("least_players", "random", "round_robin").contains(rawSelectionMode.trim().toLowerCase(Locale.ROOT))) {
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

        Config.Contextual contextual = new Config.Contextual(
                readBoolean(toml, state, "routing.contextual.enabled", defaults.routing().contextual().enabled(), "routing.contextual.enabled", "advanced_settings.use_contextual_lobbies"),
                readBoolean(toml, state, "routing.contextual.fallback_to_default", defaults.routing().contextual().fallbackToDefault(), "routing.contextual.fallback_to_default"),
                readStringListMap(toml, state, "routing.contextual.groups", "routing.contextual.groups", "contextual_lobbies.groups"),
                readStringMap(toml, state, "routing.contextual.sources", "routing.contextual.sources", "contextual_lobbies.mappings")
        );

        Config.Routing routing = new Config.Routing(
                selectionMode,
                readBoolean(toml, state, "routing.cycle_when_possible", defaults.routing().cycleWhenPossible(), "routing.cycle_when_possible", "cycle_lobbies"),
                readBoolean(toml, state, "routing.balance_initial_join", defaults.routing().balanceInitialJoin(), "routing.balance_initial_join"),
                readStringList(toml, state, "routing.default_lobbies", defaults.routing().defaultLobbies(), "routing.default_lobbies", "settings.lobby_servers", "lobby_servers"),
                contextual
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
                readString(toml, state, "messages.reload_failed", defaults.messages().reloadFailed(), "messages.reload_failed")
        );

        Config.UpdateCheckerSettings updateChecker = new Config.UpdateCheckerSettings(
                readBoolean(toml, state, "update_checker.enabled", defaults.updateChecker().enabled(), "update_checker.enabled"),
                Config.UpdateChannel.fromString(readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel")),
                readBoolean(toml, state, "update_checker.notify_console", defaults.updateChecker().notifyConsole(), "update_checker.notify_console"),
                readInt(toml, state, "update_checker.startup_delay_seconds", defaults.updateChecker().startupDelaySeconds(), "update_checker.startup_delay_seconds")
        );

        Config.MetricsSettings metrics = new Config.MetricsSettings(
                readBoolean(toml, state, "metrics.enabled", defaults.metrics().enabled(), "metrics.enabled")
        );

        Config.DebugSettings debug = new Config.DebugSettings(
                readBoolean(toml, state, "debug.verbose_logging", defaults.debug().verboseLogging(), "debug.verbose_logging")
        );

        return new Config(
                Config.CURRENT_VERSION,
                commands,
                routing,
                healthChecks,
                messages,
                updateChecker,
                metrics,
                debug
        );
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

    private Map<String, String> readStringMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey()));
                if (entry.getValue() instanceof String text && !text.isBlank()) {
                    values.put(key, text.trim());
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
        builder.append("# VelocityNavigator v3 configuration").append('\n');
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
        builder.append("#   \"least_players\" - Sends to the lobby with the fewest players. Best for even distribution.").append('\n');
        builder.append("#   \"round_robin\"   - Deals players sequentially: Lobby 1, 2, 1, 2... Ignores player counts.").append('\n');
        builder.append("#   \"random\"        - Picks a lobby at random. Best for very large networks (500+ joins/min).").append('\n');
        builder.append("#").append('\n');
        builder.append("# See docs/routing-algorithms.md for detailed math and scenarios.").append('\n');
        builder.append("selection_mode = ").append(quoted(config.routing().selectionMode().configValue())).append('\n');
        builder.append("# Prefer a different lobby when the player is already in the target pool.").append('\n');
        builder.append("cycle_when_possible = ").append(config.routing().cycleWhenPossible()).append('\n');
        builder.append("# Use the plugin's routing brain for initial proxy connections instead of Velocity's static 'try' list.").append('\n');
        builder.append("# When true, players are load-balanced the moment they connect (not just when they run /lobby).").append('\n');
        builder.append("balance_initial_join = ").append(config.routing().balanceInitialJoin()).append('\n');
        builder.append("# Default lobbies used when no contextual group wins.").append('\n');
        builder.append("default_lobbies = ").append(formatList(config.routing().defaultLobbies())).append('\n').append('\n');

        builder.append("[routing.contextual]").append('\n');
        builder.append("# Enable source-server aware routing.").append('\n');
        builder.append("enabled = ").append(config.routing().contextual().enabled()).append('\n');
        builder.append("# Fall back to routing.default_lobbies if the contextual group is empty or offline.").append('\n');
        builder.append("fallback_to_default = ").append(config.routing().contextual().fallbackToDefault()).append('\n').append('\n');

        builder.append("[routing.contextual.groups]").append('\n');
        for (Map.Entry<String, List<String>> entry : config.routing().contextual().groups().entrySet()) {
            builder.append(entry.getKey()).append(" = ").append(formatList(entry.getValue())).append('\n');
        }
        builder.append('\n');

        builder.append("[routing.contextual.sources]").append('\n');
        for (Map.Entry<String, String> entry : config.routing().contextual().sources().entrySet()) {
            builder.append(quoted(entry.getKey())).append(" = ").append(quoted(entry.getValue())).append('\n');
        }
        builder.append('\n');

        builder.append("[health_checks]").append('\n');
        builder.append("# Ping candidate lobbies before routing.").append('\n');
        builder.append("enabled = ").append(config.healthChecks().enabled()).append('\n');
        builder.append("# How long a ping can take before the server is considered unavailable.").append('\n');
        builder.append("timeout_ms = ").append(config.healthChecks().timeoutMs()).append('\n');
        builder.append("# Cache server health for this many seconds. Use 0 to disable caching.").append('\n');
        builder.append("cache_seconds = ").append(config.healthChecks().cacheSeconds()).append('\n').append('\n');

        builder.append("[messages]").append('\n');
        builder.append("# Supported placeholders: <server>, <time>, <reason>, <mode>").append('\n');
        builder.append("connecting = ").append(quoted(config.messages().connecting())).append('\n');
        builder.append("already_connected = ").append(quoted(config.messages().alreadyConnected())).append('\n');
        builder.append("no_lobby_found = ").append(quoted(config.messages().noLobbyFound())).append('\n');
        builder.append("player_only = ").append(quoted(config.messages().playerOnly())).append('\n');
        builder.append("cooldown = ").append(quoted(config.messages().cooldown())).append('\n');
        builder.append("reload_success = ").append(quoted(config.messages().reloadSuccess())).append('\n');
        builder.append("reload_failed = ").append(quoted(config.messages().reloadFailed())).append('\n').append('\n');

        builder.append("[update_checker]").append('\n');
        builder.append("# Check Modrinth once during startup.").append('\n');
        builder.append("enabled = ").append(config.updateChecker().enabled()).append('\n');
        builder.append("# Options: release, beta, alpha").append('\n');
        builder.append("channel = ").append(quoted(config.updateChecker().channel().configValue())).append('\n');
        builder.append("notify_console = ").append(config.updateChecker().notifyConsole()).append('\n');
        builder.append("startup_delay_seconds = ").append(config.updateChecker().startupDelaySeconds()).append('\n').append('\n');

        builder.append("[metrics]").append('\n');
        builder.append("# bStats support. This can be disabled if your network requires it.").append('\n');
        builder.append("enabled = ").append(config.metrics().enabled()).append('\n').append('\n');

        builder.append("[debug]").append('\n');
        builder.append("# Adds extra routing diagnostics to the console.").append('\n');
        builder.append("verbose_logging = ").append(config.debug().verboseLogging()).append('\n');

        Files.writeString(configPath, builder.toString());
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
