package com.demonz.velocitynavigator;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Config {

    private static final int CURRENT_CONFIG_VERSION = 2;

    private static final List<String> DEFAULT_LOBBY_SERVERS = Arrays.asList("lobby1", "lobby2");
    private static final String DEFAULT_SELECTION_MODE = "LEAST_PLAYERS";
    private static final List<String> DEFAULT_ALIASES = Arrays.asList("hub", "spawn");
    private static final boolean DEFAULT_RECONNECT = true;
    private static final int DEFAULT_COOLDOWN = 3;
    private static final boolean DEFAULT_CYCLE_LOBBIES = true;
    private static final boolean DEFAULT_PING_CONNECT = true;
    private static final int DEFAULT_PING_CACHE = 60;
    private static final boolean DEFAULT_USE_CONTEXTUAL = false;
    private static final Map<String, List<String>> DEFAULT_GROUPS = new HashMap<>() {{
        put("default", Arrays.asList("lobby-1", "lobby-2"));
        put("minigames", Collections.singletonList("mg_lobby"));
    }};
    private static final Map<String, String> DEFAULT_MAPPINGS = new HashMap<>() {{
        put("bedwars-1", "minigames");
        put("skywars-1", "minigames");
    }};

    private int configVersion;
    private List<String> lobbyServers;
    private String selectionMode;
    private List<String> commandAliases;
    private boolean reconnectOnLobbyCommand;
    private int commandCooldown;
    private boolean cycleLobbies;
    private boolean pingBeforeConnect;
    private int pingCacheDuration;
    private boolean useContextualLobbies;
    private ContextualLobbies contextualLobbies;
    private MessagesConfig messages;

    public List<String> getLobbyServers() { return lobbyServers; }
    public String getSelectionMode() { return selectionMode; }
    public List<String> getCommandAliases() { return commandAliases; }
    public boolean isReconnectOnLobbyCommand() { return reconnectOnLobbyCommand; }
    public int getCommandCooldown() { return commandCooldown; }
    public boolean isCycleLobbies() { return cycleLobbies; }
    public boolean isPingBeforeConnect() { return pingBeforeConnect; }
    public int getPingCacheDuration() { return pingCacheDuration; }
    public boolean isUseContextualLobbies() { return useContextualLobbies; }
    public ContextualLobbies getContextualLobbies() { return contextualLobbies; }
    public MessagesConfig getMessages() { return messages; }
    
    public static class ContextualLobbies {
        private Map<String, List<String>> groups;
        private Map<String, String> mappings;
        public Map<String, List<String>> getGroups() { return groups; }
        public Map<String, String> getMappings() { return mappings; }
    }
    public static class MessagesConfig {
        private String connecting, alreadyConnected, noLobbyFound, playerOnly, cooldown;
        public String getConnecting() { return connecting; }
        public String getAlreadyConnected() { return alreadyConnected; }
        public String getNoLobbyFound() { return noLobbyFound; }
        public String getPlayerOnly() { return playerOnly; }
        public String getCooldown() { return cooldown; }
    }

    private Config() {
        this.messages = new MessagesConfig();
        this.contextualLobbies = new ContextualLobbies();
    }

    public static Config load(Path dataDirectory, Logger logger) throws IOException {
        File configFile = dataDirectory.resolve("navigator.toml").toFile();
        configFile.getParentFile().mkdirs();

        Config config = new Config();
        if (!configFile.exists()) {
            logger.info("No config file found, creating a new one...");
            config.setAllDefaults();
        } else {
            config.loadValuesFromToml(new Toml().read(configFile));
        }
        saveConfig(configFile, config);
        return config;
    }

    private void setAllDefaults() {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.lobbyServers = DEFAULT_LOBBY_SERVERS;
        this.selectionMode = DEFAULT_SELECTION_MODE;
        this.commandAliases = DEFAULT_ALIASES;
        this.reconnectOnLobbyCommand = DEFAULT_RECONNECT;
        this.commandCooldown = DEFAULT_COOLDOWN;
        this.cycleLobbies = DEFAULT_CYCLE_LOBBIES;
        this.pingBeforeConnect = DEFAULT_PING_CONNECT;
        this.pingCacheDuration = DEFAULT_PING_CACHE;
        this.useContextualLobbies = DEFAULT_USE_CONTEXTUAL;
        this.contextualLobbies.groups = DEFAULT_GROUPS;
        this.contextualLobbies.mappings = DEFAULT_MAPPINGS;
        this.messages.connecting = "<aqua>Whoosh! Sending you to the lobby...</aqua>";
        this.messages.alreadyConnected = "<yellow>Hey! You're already in a lobby.</yellow>";
        this.messages.noLobbyFound = "<red>Oops! We couldn't find an available lobby.</red>";
        this.messages.playerOnly = "<gray>This command is for players only.</gray>";
        this.messages.cooldown = "<yellow>Whoa there! Please wait <time> more second(s).</yellow>";
    }

    @SuppressWarnings("unchecked")
    private void loadValuesFromToml(Toml toml) {
        this.configVersion = toml.getLong("config_version", (long) CURRENT_CONFIG_VERSION).intValue();
        this.lobbyServers = toml.getList("lobby_servers", DEFAULT_LOBBY_SERVERS);
        this.selectionMode = toml.getString("selection_mode", DEFAULT_SELECTION_MODE);
        this.commandAliases = toml.getList("command_aliases", DEFAULT_ALIASES);
        this.reconnectOnLobbyCommand = toml.getBoolean("reconnect_on_lobby_command", DEFAULT_RECONNECT);
        this.commandCooldown = toml.getLong("command_cooldown", (long) DEFAULT_COOLDOWN).intValue();
        this.cycleLobbies = toml.getBoolean("cycle_lobbies", DEFAULT_CYCLE_LOBBIES);
        this.pingBeforeConnect = toml.getBoolean("ping_before_connect", DEFAULT_PING_CONNECT);
        this.pingCacheDuration = toml.getLong("ping_cache_duration", (long) DEFAULT_PING_CACHE).intValue();

        Toml messagesToml = toml.getTable("messages");
        if (messagesToml != null) {
            this.messages.connecting = messagesToml.getString("connecting", this.messages.connecting);
            this.messages.alreadyConnected = messagesToml.getString("already_connected", this.messages.alreadyConnected);
            this.messages.noLobbyFound = messagesToml.getString("no_lobby_found", this.messages.noLobbyFound);
            this.messages.playerOnly = messagesToml.getString("player_only", this.messages.playerOnly);
            this.messages.cooldown = messagesToml.getString("cooldown", this.messages.cooldown);
        }

        Toml advancedToml = toml.getTable("advanced_settings");
        if (advancedToml != null) {
            this.useContextualLobbies = advancedToml.getBoolean("use_contextual_lobbies", DEFAULT_USE_CONTEXTUAL);
            Toml groupsToml = advancedToml.getTable("contextual_lobbies.groups");
            if (groupsToml != null) {
                this.contextualLobbies.groups = (Map<String, List<String>>) (Map) groupsToml.toMap();
            }
            Toml mappingsToml = advancedToml.getTable("contextual_lobbies.mappings");
            if (mappingsToml != null) {
                this.contextualLobbies.mappings = (Map<String, String>) (Map) mappingsToml.toMap();
            }
        }
    }

    private static String formatStringList(List<String> list) {
        return "[" + list.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")) + "]";
    }

    private static void saveConfig(File configFile, Config config) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("# A DemonZDevelopment Project - VelocityNavigator");
            writer.println("# This number helps the plugin manage future updates. Please do not change it.");
            writer.println("config_version = " + config.configVersion);
            writer.println("\n# --- [ Basic Settings ] ---");
            writer.println("lobby_servers = " + formatStringList(config.getLobbyServers()));
            writer.println("selection_mode = \"" + config.getSelectionMode() + "\"");
            writer.println("command_aliases = " + formatStringList(config.getCommandAliases()));
            writer.println("reconnect_on_lobby_command = " + config.isReconnectOnLobbyCommand());
            writer.println("command_cooldown = " + config.getCommandCooldown());
            writer.println("cycle_lobbies = " + config.isCycleLobbies());
            writer.println("\n# --- [ Network Resiliency Settings ] ---");
            writer.println("ping_before_connect = " + config.isPingBeforeConnect());
            writer.println("ping_cache_duration = " + config.getPingCacheDuration());
            writer.println("\n# --- [ Messages ] ---");
            writer.println("[messages]");
            writer.println("connecting = '" + config.getMessages().getConnecting() + "'");
            writer.println("already_connected = '" + config.getMessages().getAlreadyConnected() + "'");
            writer.println("no_lobby_found = '" + config.getMessages().getNoLobbyFound() + "'");
            writer.println("player_only = '" + config.getMessages().getPlayerOnly() + "'");
            writer.println("cooldown = '" + config.getMessages().getCooldown() + "'");
            writer.println("\n# --- [ Advanced Settings ] ---");
            writer.println("[advanced_settings]");
            writer.println("use_contextual_lobbies = " + config.isUseContextualLobbies());
            writer.println("[advanced_settings.contextual_lobbies]");
            writer.println("  [advanced_settings.contextual_lobbies.groups]");
            config.getContextualLobbies().getGroups().forEach((key, value) ->
                writer.println("    " + key + " = " + formatStringList(value))
            );
            writer.println("  [advanced_settings.contextual_lobbies.mappings]");
            config.getContextualLobbies().getMappings().forEach((key, value) ->
                writer.println("    \"" + key + "\" = \"" + value + "\"")
            );
        }
    }
}