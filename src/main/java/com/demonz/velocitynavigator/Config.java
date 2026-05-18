package com.demonz.velocitynavigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Config {

    public static final int CURRENT_VERSION = 4;

    private final int configVersion;
    private final Commands commands;
    private final Routing routing;
    private final HealthChecks healthChecks;
    private final Messages messages;
    private final UpdateCheckerSettings updateChecker;
    private final MetricsSettings metrics;
    private final DebugSettings debug;
    private final CircuitBreakerSettings circuitBreaker;
    private final DegradationSettings degradation;
    private final GeoRoutingSettings geoRouting;
    private final boolean notifyOnStartup;
    private final boolean notifyAdminsOnJoin;

    public Config(
            int configVersion,
            Commands commands,
            Routing routing,
            HealthChecks healthChecks,
            Messages messages,
            UpdateCheckerSettings updateChecker,
            MetricsSettings metrics,
            DebugSettings debug,
            CircuitBreakerSettings circuitBreaker,
            DegradationSettings degradation,
            GeoRoutingSettings geoRouting,
            boolean notifyOnStartup,
            boolean notifyAdminsOnJoin
    ) {
        this.configVersion = configVersion;
        this.commands = commands;
        this.routing = routing;
        this.healthChecks = healthChecks;
        this.messages = messages;
        this.updateChecker = updateChecker;
        this.metrics = metrics;
        this.debug = debug;
        this.circuitBreaker = circuitBreaker;
        this.degradation = degradation;
        this.geoRouting = geoRouting;
        this.notifyOnStartup = notifyOnStartup;
        this.notifyAdminsOnJoin = notifyAdminsOnJoin;
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
                        List.of(new LobbyEntry("lobby-1", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT),
                                new LobbyEntry("lobby-2", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT)),
                        new Contextual(
                                false,
                                true,
                                Map.of("bedwars", new GroupConfig(
                                        List.of(new LobbyEntry("bedwars-lobby-1", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT),
                                                new LobbyEntry("bedwars-lobby-2", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT)),
                                        null
                                )),
                                Map.of("bedwars-1", "bedwars"),
                                Map.of()
                        ),
                        2
                ),
                new HealthChecks(true, 2500, 60),
                new Messages(
                        "<aqua>Sending you to <server>...</aqua>",
                        "<yellow>You are already connected to <server>.</yellow>",
                        "<red>No available lobby could be found. (<reason>)</red>",
                        "<gray>This command can only be used by a player.</gray>",
                        "<yellow>Please wait <time> more second(s).</yellow>",
                        "<green>VelocityNavigator reloaded.</green>",
                        "<red>Reload failed. Check console for details.</red>",
                        "<yellow>Retrying connection... (<attempt>/<max>)</yellow>"
                ),
                new UpdateCheckerSettings(UpdateChannel.RELEASE),
                new MetricsSettings(true),
                new DebugSettings(false),
                new CircuitBreakerSettings(true, 3, 30, 1),
                new DegradationSettings(true, "random"),
                new GeoRoutingSettings(false, ""),
                true,
                true
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

    public CircuitBreakerSettings circuitBreaker() {
        return circuitBreaker;
    }

    public DegradationSettings degradation() {
        return degradation;
    }

    public GeoRoutingSettings geoRouting() {
        return geoRouting;
    }

    public boolean notifyOnStartup() {
        return notifyOnStartup;
    }

    public boolean notifyAdminsOnJoin() {
        return notifyAdminsOnJoin;
    }

    public enum SelectionMode {
        LEAST_PLAYERS,
        RANDOM,
        ROUND_ROBIN,
        POWER_OF_TWO,
        WEIGHTED_ROUND_ROBIN,
        LEAST_CONNECTIONS,
        CONSISTENT_HASH;

        public static SelectionMode fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return LEAST_PLAYERS;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "least_players" -> LEAST_PLAYERS;
                case "random" -> RANDOM;
                case "round_robin" -> ROUND_ROBIN;
                case "power_of_two" -> POWER_OF_TWO;
                case "weighted_round_robin" -> WEIGHTED_ROUND_ROBIN;
                case "least_connections" -> LEAST_CONNECTIONS;
                case "consistent_hash" -> CONSISTENT_HASH;
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

    public record LobbyEntry(String server, int maxPlayers, int weight) {
        public static final int UNCAPPED = -1;
        public static final int DEFAULT_WEIGHT = 1;

        public LobbyEntry {
            server = server == null ? "" : server.trim().toLowerCase(Locale.ROOT);
            if (server.isBlank()) {
                server = "";
            }
            if (maxPlayers < 0) {
                maxPlayers = UNCAPPED;
            }
            if (weight <= 0) {
                weight = DEFAULT_WEIGHT;
            }
        }

        public boolean isFull(int currentPlayers) {
            if (maxPlayers == UNCAPPED) {
                return false;
            }
            return currentPlayers >= maxPlayers;
        }

        public int effectiveWeight() {
            return weight;
        }
    }

    public record GroupConfig(List<LobbyEntry> servers, SelectionMode mode) {
        public GroupConfig {
            servers = servers == null ? List.of() : List.copyOf(servers);
            // mode can be null — meaning "use global default"
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
            List<LobbyEntry> defaultLobbies,
            Contextual contextual,
            int maxRetries
    ) {
        public Routing {
            selectionMode = selectionMode == null ? SelectionMode.LEAST_PLAYERS : selectionMode;
            defaultLobbies = immutableLobbyEntries(defaultLobbies);
            contextual = contextual == null ? new Contextual(false, true, Map.of(), Map.of(), Map.of()) : contextual;
            maxRetries = Math.max(0, maxRetries);
        }
    }

    public record Contextual(
            boolean enabled,
            boolean fallbackToDefault,
            Map<String, GroupConfig> groups,
            Map<String, String> sources,
            Map<String, List<String>> fallbackChain
    ) {
        public Contextual {
            groups = immutableGroupConfigMap(groups);
            sources = immutableStringMap(sources);
            fallbackChain = immutableStringListMap(fallbackChain);
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
            String reloadFailed,
            String retrying
    ) {
        public Messages {
            connecting = sanitizeText(connecting, "<aqua>Sending you to <server>...</aqua>");
            alreadyConnected = sanitizeText(alreadyConnected, "<yellow>You are already connected to <server>.</yellow>");
            noLobbyFound = sanitizeText(noLobbyFound, "<red>No available lobby could be found. (<reason>)</red>");
            playerOnly = sanitizeText(playerOnly, "<gray>This command can only be used by a player.</gray>");
            cooldown = sanitizeText(cooldown, "<yellow>Please wait <time> more second(s).</yellow>");
            reloadSuccess = sanitizeText(reloadSuccess, "<green>VelocityNavigator reloaded.</green>");
            reloadFailed = sanitizeText(reloadFailed, "<red>Reload failed. Check console for details.</red>");
            retrying = sanitizeText(retrying, "<yellow>Retrying connection... (<attempt>/<max>)</yellow>");
        }
    }

    public record UpdateCheckerSettings(
            UpdateChannel channel
    ) {
        public UpdateCheckerSettings {
            channel = channel == null ? UpdateChannel.RELEASE : channel;
        }
    }

    public record MetricsSettings(boolean enabled) {
    }

    public record DebugSettings(boolean verboseLogging) {
    }

    public record CircuitBreakerSettings(boolean enabled, int failureThreshold, int cooldownSeconds, int halfOpenMaxTests) {
        public CircuitBreakerSettings {
            failureThreshold = Math.max(1, failureThreshold);
            cooldownSeconds = Math.max(1, cooldownSeconds);
            halfOpenMaxTests = Math.max(1, halfOpenMaxTests);
        }
    }

    public record DegradationSettings(boolean enabled, String mode) {
        public DegradationSettings {
            mode = sanitizeText(mode, "random");
        }
    }

    public record GeoRoutingSettings(boolean enabled, String databasePath) {
        public GeoRoutingSettings {
            databasePath = databasePath == null ? "" : databasePath;
        }
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

    private static List<LobbyEntry> immutableLobbyEntries(List<LobbyEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<LobbyEntry> cleaned = new ArrayList<>();
        for (LobbyEntry entry : entries) {
            if (entry != null && !entry.server().isBlank()) {
                cleaned.add(entry);
            }
        }
        return Collections.unmodifiableList(cleaned);
    }

    private static Map<String, GroupConfig> immutableGroupConfigMap(Map<String, GroupConfig> groups) {
        if (groups == null || groups.isEmpty()) {
            return Map.of();
        }
        Map<String, GroupConfig> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, GroupConfig> entry : groups.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String key = entry.getKey().trim();
            GroupConfig value = entry.getValue();
            if (value != null && !value.servers().isEmpty()) {
                cleaned.put(key, value);
            }
        }
        return Collections.unmodifiableMap(cleaned);
    }

    private static Map<String, List<String>> immutableStringListMap(Map<String, List<String>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            List<String> val = immutableNames(entry.getValue(), null);
            if (!val.isEmpty()) {
                cleaned.put(entry.getKey().trim(), val);
            }
        }
        return Collections.unmodifiableMap(cleaned);
    }

    /**
     * Normalizes the sources map so that keys and values are lowercased.
     * This ensures lookups in RoutePlanner (which lowercases the source server name)
     * always match, regardless of how the user typed keys in navigator.toml.
     */
    private static Map<String, String> immutableStringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            cleaned.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue().trim().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableMap(cleaned);
    }
}
