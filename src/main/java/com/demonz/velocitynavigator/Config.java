package com.demonz.velocitynavigator;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
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

public class Config {

    private static final int CURRENT_CONFIG_VERSION = 4;

    private static final List<String> DEFAULT_ALIASES = Arrays.asList("hub", "spawn");
    private static final String DEFAULT_PERMISSION = "velocitynavigator.use";
    private static final boolean DEFAULT_MANUAL_SETUP = false;
    private static final boolean DEFAULT_RECONNECT = true;
    private static final int DEFAULT_COOLDOWN = 3;
    private static final String DEFAULT_SELECTION_MODE = "LEAST_PLAYERS";
    private static final List<String> DEFAULT_BLACKLIST = Collections.singletonList("auth");
    private static final Map<String, List<String>> DEFAULT_SERVER_GROUPS =
        Collections.singletonMap("default", Arrays.asList("lobby1", "lobby2"));
    private static final Map<String, String> DEFAULT_GROUP_MAPPINGS =
        Collections.singletonMap("minigames_server_1", "default");

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
        private static final String DEFAULT_MSG_CONNECTING = "<green>Connecting you to the lobby...</green>";
        private static final String DEFAULT_MSG_ALREADY_CONNECTED = "<yellow>You are already connected to this lobby!</yellow>";
        private static final String DEFAULT_MSG_NO_LOBBY_FOUND = "<red>Error: No available lobby servers could be found.</red>";
        private static final String DEFAULT_MSG_PLAYER_ONLY = "<red>This command can only be run by a player.</red>";
        private static final String DEFAULT_MSG_NO_PERMISSION = "<red>You do not have permission to use this command.</red>";
        private static final String DEFAULT_MSG_COOLDOWN = "<red>Please wait <time> seconds before using this again.</red>";
        private static final String DEFAULT_MSG_DISABLED = "<red>You cannot use this command on this server.</red>";
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
            saveConfig(configFile, config, "# A DemonZDevelopment Project\n#        VelocityNavigator\n\n");
        } else {
            Toml toml = new Toml().read(configFile);
            int loadedVersion = toml.getLong("config-version", 0L).intValue();
            if (loadedVersion < CURRENT_CONFIG_VERSION) {
                logger.info("Old config (v{}) detected. Updating to v{}...", loadedVersion, CURRENT_CONFIG_VERSION);
                File backupFile = dataDirectory.resolve("navigator.toml.v" + loadedVersion + ".bak").toFile();
                if (backupFile.exists()) backupFile.delete();
                configFile.renameTo(backupFile);
                config.loadValuesFromToml(toml);
                saveConfig(configFile, config, "# A DemonZDevelopment Project\n#        VelocityNavigator\n# Your configuration has been automatically updated!\n\n");
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

    private void loadValuesFromToml(Toml toml) {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.commandAliases = toml.getList("commands.aliases", DEFAULT_ALIASES);
        this.commandPermission = toml.getString("commands.permission", DEFAULT_PERMISSION);
        this.manualLobbySetup = toml.getBoolean("settings.manualLobbySetup", DEFAULT_MANUAL_SETUP);
        this.reconnectOnLobbyCommand = toml.getBoolean("settings.reconnectOnLobbyCommand", DEFAULT_RECONNECT);
        this.commandCooldown = toml.getLong("settings.commandCooldown", (long) DEFAULT_COOLDOWN).intValue();
        this.lobbySelectionMode = toml.getString("settings.lobbySelectionMode", DEFAULT_SELECTION_MODE);
        this.blacklistFromServers = toml.getList("settings.blacklistFromServers", DEFAULT_BLACKLIST);
        
        // --- FIX STARTS HERE ---
        Toml serverGroupsToml = toml.getTable("serverGroups");
        this.serverGroups = (serverGroupsToml != null) ? serverGroupsToml.toMap() : DEFAULT_SERVER_GROUPS;

        Toml serverGroupMappingsToml = toml.getTable("serverGroupMappings");
        this.serverGroupMappings = (serverGroupMappingsToml != null) ? serverGroupMappingsToml.toMap() : DEFAULT_GROUP_MAPPINGS;

        Toml messagesToml = toml.getTable("messages");
        if (messagesToml == null) {
            messagesToml = new Toml(); // Create empty Toml if section doesn't exist
        }
        // --- FIX ENDS HERE ---

        this.messages.connecting = messagesToml.getString("connecting", MessagesConfig.DEFAULT_MSG_CONNECTING);
        this.messages.alreadyConnected = messagesToml.getString("already-connected", MessagesConfig.DEFAULT_MSG_ALREADY_CONNECTED);
        this.messages.noLobbyFound = messagesToml.getString("no-lobby-found", MessagesConfig.DEFAULT_MSG_NO_LOBBY_FOUND);
        this.messages.playerOnly = messagesToml.getString("player-only", MessagesConfig.DEFAULT_MSG_PLAYER_ONLY);
        this.messages.noPermission = messagesToml.getString("no-permission", MessagesConfig.DEFAULT_MSG_NO_PERMISSION);
        this.messages.cooldown = messagesToml.getString("cooldown", MessagesConfig.DEFAULT_MSG_COOLDOWN);
        this.messages.commandDisabled = messagesToml.getString("command-disabled", MessagesConfig.DEFAULT_MSG_DISABLED);
    }

    private static void saveConfig(File configFile, Config config, String header) throws IOException {
        Map<String, Object> root = new HashMap<>();
        root.put("config-version", config.configVersion);
        Map<String, Object> commands = new HashMap<>();
        commands.put("aliases", config.getCommandAliases());
        commands.put("permission", config.getCommandPermission());
        root.put("commands", commands);
        Map<String, Object> settings = new HashMap<>();
        settings.put("manualLobbySetup", config.isManualLobbySetup());
        settings.put("reconnectOnLobbyCommand", config.isReconnectOnLobbyCommand());
        settings.put("commandCooldown", config.getCommandCooldown());
        settings.put("lobbySelectionMode", config.getLobbySelectionMode());
        settings.put("blacklistFromServers", config.getBlacklistFromServers());
        root.put("settings", settings);
        Map<String, Object> messages = new HashMap<>();
        messages.put("connecting", config.getMessages().getConnecting());
        messages.put("already-connected", config.getMessages().getAlreadyConnected());
        messages.put("no-lobby-found", config.getMessages().getNoLobbyFound());
        messages.put("player-only", config.getMessages().getPlayerOnly());
        messages.put("no-permission", config.getMessages().getNoPermission());
        messages.put("cooldown", config.getMessages().getCooldown());
        messages.put("command-disabled", config.getMessages().getCommandDisabled());
        root.put("messages", messages);
        root.put("serverGroups", config.getServerGroups());
        root.put("serverGroupMappings", config.getServerGroupMappings());
        String tomlString = new TomlWriter().write(root);
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.print(header + tomlString);
        }
    }
}