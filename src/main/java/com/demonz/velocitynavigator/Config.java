package com.demonz.velocitynavigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Config {

    public static final int CURRENT_VERSION = 3;

    private final int configVersion;
    private final Commands commands;
    private final Routing routing;
    private final HealthChecks healthChecks;
    private final Messages messages;
    private final UpdateCheckerSettings updateChecker;
    private final MetricsSettings metrics;
    private final DebugSettings debug;

    public Config(
            int configVersion,
            Commands commands,
            Routing routing,
            HealthChecks healthChecks,
            Messages messages,
            UpdateCheckerSettings updateChecker,
            MetricsSettings metrics,
            DebugSettings debug
    ) {
        this.configVersion = configVersion;
        this.commands = commands;
        this.routing = routing;
        this.healthChecks = healthChecks;
        this.messages = messages;
        this.updateChecker = updateChecker;
        this.metrics = metrics;
        this.debug = debug;
    }

    public static Config defaults() {
        return new Config(
                CURRENT_VERSION,
                new Commands(
                        "lobby",
                        List.of("hub", "spawn"),
                        "velocitynavigator.use",
                        List.of("velocitynavigator", "vn"),
                        3,
                        false
                ),
                new Routing(
                        SelectionMode.LEAST_PLAYERS,
                        true,
                        true,
                        List.of("lobby-1", "lobby-2"),
                        new Contextual(
                                false,
                                true,
                                Map.of("bedwars", List.of("bedwars-lobby-1", "bedwars-lobby-2")),
                                Map.of("bedwars-1", "bedwars")
                        )
                ),
                new HealthChecks(true, 2500, 60),
                new Messages(
                        "<aqua>Sending you to <server>...</aqua>",
                        "<yellow>You are already connected to <server>.</yellow>",
                        "<red>No available lobby could be found.</red>",
                        "<gray>This command can only be used by a player.</gray>",
                        "<yellow>Please wait <time> more second(s).</yellow>",
                        "<green>VelocityNavigator reloaded.</green>",
                        "<red>Reload failed. Check console for details.</red>"
                ),
                new UpdateCheckerSettings(true, UpdateChannel.RELEASE, true, 5),
                new MetricsSettings(true),
                new DebugSettings(false)
        );
    }

    public int configVersion() {
        return configVersion;
    }

    public Commands commands() {
        return commands;
    }

    public Routing routing() {
        return routing;
    }

    public HealthChecks healthChecks() {
        return healthChecks;
    }

    public Messages messages() {
        return messages;
    }

    public UpdateCheckerSettings updateChecker() {
        return updateChecker;
    }

    public MetricsSettings metrics() {
        return metrics;
    }

    public DebugSettings debug() {
        return debug;
    }

    public enum SelectionMode {
        LEAST_PLAYERS,
        RANDOM,
        ROUND_ROBIN;

        public static SelectionMode fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return LEAST_PLAYERS;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "least_players" -> LEAST_PLAYERS;
                case "random" -> RANDOM;
                case "round_robin" -> ROUND_ROBIN;
                default -> LEAST_PLAYERS;
            };
        }

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum UpdateChannel {
        RELEASE,
        BETA,
        ALPHA;

        public static UpdateChannel fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return RELEASE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "beta" -> BETA;
                case "alpha" -> ALPHA;
                default -> RELEASE;
            };
        }

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum RemoteVersionType {
        RELEASE,
        BETA,
        ALPHA;

        public static RemoteVersionType fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return RELEASE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "beta" -> BETA;
                case "alpha" -> ALPHA;
                default -> RELEASE;
            };
        }
    }

    public record Commands(
            String primary,
            List<String> aliases,
            String permission,
            List<String> adminAliases,
            int cooldownSeconds,
            boolean reconnectIfSameServer
    ) {
        public Commands {
            primary = sanitizeCommand(primary, "lobby");
            aliases = immutableNames(aliases, primary);
            permission = sanitizeText(permission, "velocitynavigator.use");
            adminAliases = immutableNames(adminAliases, null);
            if (adminAliases.isEmpty()) {
                adminAliases = List.of("velocitynavigator", "vn");
            }
            cooldownSeconds = Math.max(0, cooldownSeconds);
        }
    }

    public record Routing(
            SelectionMode selectionMode,
            boolean cycleWhenPossible,
            boolean balanceInitialJoin,
            List<String> defaultLobbies,
            Contextual contextual
    ) {
        public Routing {
            selectionMode = selectionMode == null ? SelectionMode.LEAST_PLAYERS : selectionMode;
            defaultLobbies = immutableNames(defaultLobbies, null);
            contextual = contextual == null ? new Contextual(false, true, Map.of(), Map.of()) : contextual;
        }
    }

    public record Contextual(
            boolean enabled,
            boolean fallbackToDefault,
            Map<String, List<String>> groups,
            Map<String, String> sources
    ) {
        public Contextual {
            groups = immutableGroupMap(groups);
            sources = immutableStringMap(sources);
        }
    }

    public record HealthChecks(boolean enabled, int timeoutMs, int cacheSeconds) {
        public HealthChecks {
            timeoutMs = Math.max(250, timeoutMs);
            cacheSeconds = Math.max(0, cacheSeconds);
        }
    }

    public record Messages(
            String connecting,
            String alreadyConnected,
            String noLobbyFound,
            String playerOnly,
            String cooldown,
            String reloadSuccess,
            String reloadFailed
    ) {
        public Messages {
            connecting = sanitizeText(connecting, "<aqua>Sending you to <server>...</aqua>");
            alreadyConnected = sanitizeText(alreadyConnected, "<yellow>You are already connected to <server>.</yellow>");
            noLobbyFound = sanitizeText(noLobbyFound, "<red>No available lobby could be found.</red>");
            playerOnly = sanitizeText(playerOnly, "<gray>This command can only be used by a player.</gray>");
            cooldown = sanitizeText(cooldown, "<yellow>Please wait <time> more second(s).</yellow>");
            reloadSuccess = sanitizeText(reloadSuccess, "<green>VelocityNavigator reloaded.</green>");
            reloadFailed = sanitizeText(reloadFailed, "<red>Reload failed. Check console for details.</red>");
        }
    }

    public record UpdateCheckerSettings(
            boolean enabled,
            UpdateChannel channel,
            boolean notifyConsole,
            int startupDelaySeconds
    ) {
        public UpdateCheckerSettings {
            channel = channel == null ? UpdateChannel.RELEASE : channel;
            startupDelaySeconds = Math.max(0, startupDelaySeconds);
        }
    }

    public record MetricsSettings(boolean enabled) {
    }

    public record DebugSettings(boolean verboseLogging) {
    }

    private static String sanitizeCommand(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replace("/", "");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static String sanitizeText(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    private static List<String> immutableNames(List<String> names, String excludedName) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String normalized = sanitizeCommand(name, "");
            if (normalized.isBlank()) {
                continue;
            }
            if (excludedName != null && normalized.equalsIgnoreCase(excludedName)) {
                continue;
            }
            if (!cleaned.contains(normalized)) {
                cleaned.add(normalized);
            }
        }
        return Collections.unmodifiableList(cleaned);
    }

    private static Map<String, List<String>> immutableGroupMap(Map<String, List<String>> groups) {
        if (groups == null || groups.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String key = entry.getKey().trim();
            List<String> value = immutableNames(entry.getValue(), null);
            if (!value.isEmpty()) {
                cleaned.put(key, value);
            }
        }
        return Collections.unmodifiableMap(cleaned);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            cleaned.put(entry.getKey().trim(), entry.getValue().trim());
        }
        return Collections.unmodifiableMap(cleaned);
    }
}
