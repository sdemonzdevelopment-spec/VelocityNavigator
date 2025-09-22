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

    private static final int CURRENT_CONFIG_VERSION = 5;

    // --- Updated, Friendlier Defaults ---
    private static final List<String> DEFAULT_ALIASES = Arrays.asList("hub", "spawn", "l");
    private static final String DEFAULT_PERMISSION = "velocitynavigator.use";
    private static final boolean DEFAULT_MANUAL_SETUP = false;
    private static final boolean DEFAULT_RECONNECT = true;
    private static final int DEFAULT_COOLDOWN = 3;
    private static final String DEFAULT_SELECTION_MODE = "LEAST_PLAYERS";
    private static final List<String> DEFAULT_BLACKLIST = Collections.singletonList("auth");
    
    private static final Map<String, List<String>> DEFAULT_SERVER_GROUPS = new HashMap<>() {{
        put("default", Arrays.asList("lobby-1", "lobby-2"));
        put("minigames", Collections.singletonList("mg_lobby"));
    }};
    private static final Map<String, String> DEFAULT_GROUP_MAPPINGS = new HashMap<>() {{
        put("bedwars-1", "minigames");
        put("skywars-1", "minigames");
        put("survival", "default");
    }};

    private int configVersion;
    private List<String> commandAliases;
    private String commandPermission;
    private boolean manualLobbySetup;
    private boolean reconnectOnLobbyCommand;
    private int commandCooldown;
    private String lobbySelectionMode;
    private List<String> blacklistFromServers;
    private Map<String, List<String>> serverGroups;
    private Map<String, String> serverGroupMappings;
    private MessagesConfig messages;

    public List<String> getCommandAliases() { return commandAliases; }
    public String getCommandPermission() { return commandPermission; }
    public boolean isManualLobbySetup() { return manualLobbySetup; }
    public boolean isReconnectOnLobbyCommand() { return reconnectOnLobbyCommand; }
    public int getCommandCooldown() { return commandCooldown; }
    public String getLobbySelectionMode() { return lobbySelectionMode; }
    public List<String> getBlacklistFromServers() { return blacklistFromServers; }
    public Map<String, List<String>> getServerGroups() { return serverGroups; }
    public Map<String, String> getServerGroupMappings() { return serverGroupMappings; }
    public MessagesConfig getMessages() { return messages; }

    public static class MessagesConfig {
        private static final String DEFAULT_MSG_CONNECTING = "<aqua>Whoosh! Sending you to the lobby...</aqua>";
        private static final String DEFAULT_MSG_ALREADY_CONNECTED = "<yellow>Hey! You're already in a lobby.</yellow>";
        private static final String DEFAULT_MSG_NO_LOBBY_FOUND = "<red>Oops! We couldn't find a lobby. Please let a staff member know.</red>";
        private static final String DEFAULT_MSG_PLAYER_ONLY = "<gray>This command is for players only, sorry!</gray>";
        private static final String DEFAULT_MSG_NO_PERMISSION = "<red>Access Denied! You don't have permission for that.</red>";
        private static final String DEFAULT_MSG_COOLDOWN = "<yellow>Whoa there! Please wait <time> more second(s).</yellow>";
        private static final String DEFAULT_MSG_DISABLED = "<red>Sorry, the /lobby command is disabled on this server.</red>";
        String connecting, alreadyConnected, noLobbyFound, playerOnly, noPermission, cooldown, commandDisabled;
        public String getConnecting() { return connecting; }
        public String getAlreadyConnected() { return alreadyConnected; }
        public String getNoLobbyFound() { return noLobbyFound; }
        public String getPlayerOnly() { return playerOnly; }
        public String getNoPermission() { return noPermission; }
        public String getCooldown() { return cooldown; }
        public String getCommandDisabled() { return commandDisabled; }
    }

    private Config() {
        this.messages = new MessagesConfig();
    }

    public static Config load(Path dataDirectory, Logger logger) throws IOException {
        File configFile = dataDirectory.resolve("navigator.toml").toFile();
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        Config config = new Config();
        if (!configFile.exists()) {
            logger.info("No config file found, creating a new one (v{})...", CURRENT_CONFIG_VERSION);
            config.setAllDefaults();
            saveConfig(configFile, config);
        } else {
            Toml toml = new Toml().read(configFile);
            int loadedVersion = toml.getLong("config-version", 0L).intValue();
            if (loadedVersion < CURRENT_CONFIG_VERSION) {
                logger.info("Old config (v{}) detected. Updating to v{}...", loadedVersion, CURRENT_CONFIG_VERSION);
                File backupFile = dataDirectory.resolve("navigator.toml.v" + loadedVersion + ".bak").toFile();
                if (backupFile.exists()) backupFile.delete();
                configFile.renameTo(backupFile);
                config.loadValuesFromToml(toml);
                saveConfig(configFile, config);
                logger.info("Config update complete. Old config backed up to {}", backupFile.getName());
            } else {
                config.loadValuesFromToml(toml);
            }
        }
        return config;
    }

    private void setAllDefaults() {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.commandAliases = DEFAULT_ALIASES;
        this.commandPermission = DEFAULT_PERMISSION;
        this.manualLobbySetup = DEFAULT_MANUAL_SETUP;
        this.reconnectOnLobbyCommand = DEFAULT_RECONNECT;
        this.commandCooldown = DEFAULT_COOLDOWN;
        this.lobbySelectionMode = DEFAULT_SELECTION_MODE;
        this.blacklistFromServers = DEFAULT_BLACKLIST;
        this.serverGroups = DEFAULT_SERVER_GROUPS;
        this.serverGroupMappings = DEFAULT_GROUP_MAPPINGS;
        this.messages.connecting = MessagesConfig.DEFAULT_MSG_CONNECTING;
        this.messages.alreadyConnected = MessagesConfig.DEFAULT_MSG_ALREADY_CONNECTED;
        this.messages.noLobbyFound = MessagesConfig.DEFAULT_MSG_NO_LOBBY_FOUND;
        this.messages.playerOnly = MessagesConfig.DEFAULT_MSG_PLAYER_ONLY;
        this.messages.noPermission = MessagesConfig.DEFAULT_MSG_NO_PERMISSION;
        this.messages.cooldown = MessagesConfig.DEFAULT_MSG_COOLDOWN;
        this.messages.commandDisabled = MessagesConfig.DEFAULT_MSG_DISABLED;
    }

    @SuppressWarnings("unchecked")
    private void loadValuesFromToml(Toml toml) {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.commandAliases = toml.getList("commands.aliases", DEFAULT_ALIASES);
        this.commandPermission = toml.getString("commands.permission", DEFAULT_PERMISSION);
        this.manualLobbySetup = toml.getBoolean("settings.manualLobbySetup", DEFAULT_MANUAL_SETUP);
        this.reconnectOnLobbyCommand = toml.getBoolean("settings.reconnectOnLobbyCommand", DEFAULT_RECONNECT);
        this.commandCooldown = toml.getLong("settings.commandCooldown", (long) DEFAULT_COOLDOWN).intValue();
        this.lobbySelectionMode = toml.getString("settings.lobbySelectionMode", DEFAULT_SELECTION_MODE);
        this.blacklistFromServers = toml.getList("settings.blacklistFromServers", DEFAULT_BLACKLIST);
        
        Toml serverGroupsToml = toml.getTable("serverGroups");
        if (serverGroupsToml != null) {
            this.serverGroups = (Map<String, List<String>>) (Map) serverGroupsToml.toMap();
        } else {
            this.serverGroups = DEFAULT_SERVER_GROUPS;
        }

        Toml serverGroupMappingsToml = toml.getTable("serverGroupMappings");
        if (serverGroupMappingsToml != null) {
            this.serverGroupMappings = (Map<String, String>) (Map) serverGroupMappingsToml.toMap();
        } else {
            this.serverGroupMappings = DEFAULT_GROUP_MAPPINGS;
        }

        Toml messagesToml = toml.getTable("messages");
        if (messagesToml == null) {
            messagesToml = new Toml(); 
        }

        this.messages.connecting = messagesToml.getString("connecting", MessagesConfig.DEFAULT_MSG_CONNECTING);
        this.messages.alreadyConnected = messagesToml.getString("already-connected", MessagesConfig.DEFAULT_MSG_ALREADY_CONNECTED);
        this.messages.noLobbyFound = messagesToml.getString("no-lobby-found", MessagesConfig.DEFAULT_MSG_NO_LOBBY_FOUND);
        this.messages.playerOnly = messagesToml.getString("player-only", MessagesConfig.DEFAULT_MSG_PLAYER_ONLY);
        this.messages.noPermission = messagesToml.getString("no-permission", MessagesConfig.DEFAULT_MSG_NO_PERMISSION);
        this.messages.cooldown = messagesToml.getString("cooldown", MessagesConfig.DEFAULT_MSG_COOLDOWN);
        this.messages.commandDisabled = messagesToml.getString("command-disabled", MessagesConfig.DEFAULT_MSG_DISABLED);
    }

    private static String formatStringList(List<String> list) {
        return "[" + list.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")) + "]";
    }
    
    private static void saveConfig(File configFile, Config config) throws IOException {
        String header = config.configVersion == CURRENT_CONFIG_VERSION
            ? "# A DemonZDevelopment Project\n#        VelocityNavigator\n\n"
            : "# A DemonZDevelopment Project\n#        VelocityNavigator\n# Your configuration has been automatically updated!\n\n";

        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println(header);
            writer.println("# This number is used by the plugin to know when to update your config. Do not change it.");
            writer.println("config-version = " + config.configVersion);
            writer.println();

            writer.println("# --- [ Commands ] ---");
            writer.println("# permission: The permission node required to use the /lobby command.");
            writer.println("# aliases: A list of other commands that will act like /lobby.");
            writer.println("[commands]");
            writer.println("permission = \"" + config.getCommandPermission() + "\"");
            writer.println("aliases = " + formatStringList(config.getCommandAliases()));
            writer.println();

            writer.println("# --- [ Settings ] ---");
            writer.println("# manualLobbySetup: If false, the plugin will ALWAYS look for a server named exactly \"lobby\".");
            writer.println("#                  If true, it will use the server groups defined below.");
            writer.println("# reconnectOnLobbyCommand: If true, a player can type /lobby even if they are already in a lobby server to be sent to another one.");
            writer.println("# commandCooldown: The number of seconds a player must wait before using the command again.");
            writer.println("# lobbySelectionMode: How to pick a lobby from a list. Can be \"RANDOM\" or \"LEAST_PLAYERS\".");
            writer.println("# blacklistFromServers: A list of servers where the /lobby command is completely disabled.");
            writer.println("[settings]");
            writer.println("manualLobbySetup = " + config.isManualLobbySetup());
            writer.println("reconnectOnLobbyCommand = " + config.isReconnectOnLobbyCommand());
            writer.println("commandCooldown = " + config.getCommandCooldown());
            writer.println("lobbySelectionMode = \"" + config.getLobbySelectionMode() + "\"");
            writer.println("blacklistFromServers = " + formatStringList(config.getBlacklistFromServers()));
            writer.println();

            writer.println("# --- [ Messages ] ---");
            writer.println("# All messages use the MiniMessage format for colors and styles.");
            writer.println("# Learn more here: https://docs.adventure.kyori.net/minimessage");
            writer.println("[messages]");
            writer.println("connecting = '" + config.getMessages().getConnecting() + "'");
            writer.println("already-connected = '" + config.getMessages().getAlreadyConnected() + "'");
            writer.println("no-lobby-found = '" + config.getMessages().getNoLobbyFound() + "'");
            writer.println("player-only = '" + config.getMessages().getPlayerOnly() + "'");
            writer.println("no-permission = '" + config.getMessages().getNoPermission() + "'");
            writer.println("cooldown = '" + config.getMessages().getCooldown() + "'");
            writer.println("command-disabled = '" + config.getMessages().getCommandDisabled() + "'");
            writer.println();

            writer.println("# --- [ Contextual Lobbies ] ---");
            writer.println("# This powerful feature lets you send players to different lobbies based on the server they are on.");
            writer.println();
            writer.println("# STEP 1: Define your lobby 'pools' here in [serverGroups].");
            writer.println("# The 'default' group is required and is used for any server not listed in the mappings below.");
            writer.println("[serverGroups]");
            config.getServerGroups().forEach((key, value) ->
                writer.println(key + " = " + formatStringList(value))
            );
            writer.println();

            writer.println("# STEP 2: Map your actual game servers to the lobby pools you defined above.");
            writer.println("# Format is \"your-server-name\" = \"group-name-from-above\".");
            writer.println("[serverGroupMappings]");
            config.getServerGroupMappings().forEach((key, value) ->
                writer.println("\"" + key + "\" = \"" + value + "\"")
            );
        }
    }
}
