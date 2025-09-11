package com.demonz.velocitynavigator;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {

    // These fields will be transient to avoid being written directly to TOML root
    private transient List<String> commandAliases;
    private transient boolean manualLobbySetup;
    private transient boolean reconnectOnLobbyCommand;
    private transient List<String> lobbyServers;

    // Default values
    private static final List<String> DEFAULT_ALIASES = Arrays.asList("hub", "spawn");
    private static final boolean DEFAULT_MANUAL_SETUP = false;
    private static final boolean DEFAULT_RECONNECT = true;
    private static final List<String> DEFAULT_LOBBY_SERVERS = Arrays.asList("lobby1", "lobby2", "lobby3");

    // Getters for the new config values
    public List<String> getCommandAliases() { return commandAliases; }
    public boolean isManualLobbySetup() { return manualLobbySetup; }
    public boolean isReconnectOnLobbyCommand() { return reconnectOnLobbyCommand; }
    public List<String> getLobbyServers() { return lobbyServers; }

    public static Config load(Path dataDirectory, org.slf4j.Logger logger) throws IOException {
        File configFile = dataDirectory.resolve("navigator.toml").toFile();
        
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        Config config = new Config();
        
        if (!configFile.exists()) {
            logger.info("No config file found, creating a new one...");
            // Write header first
            try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                writer.println("# A DemonZDevelopment Project");
                writer.println("#        VelocityNavigator");
                writer.println();
            }

            // Create a structured map for TomlWriter
            Map<String, Object> commands = new HashMap<>();
            commands.put("aliases", DEFAULT_ALIASES);

            Map<String, Object> settings = new HashMap<>();
            settings.put("manualLobbySetup", DEFAULT_MANUAL_SETUP);
            settings.put("reconnectOnLobbyCommand", DEFAULT_RECONNECT);
            
            Map<String, Object> fullConfig = new HashMap<>();
            fullConfig.put("commands", commands);
            fullConfig.put("settings", settings);
            fullConfig.put("lobbyServers", DEFAULT_LOBBY_SERVERS); // Kept at root for clarity

            TomlWriter tomlWriter = new TomlWriter();
            tomlWriter.write(fullConfig, new FileWriter(configFile, true));
            
            // Set defaults in the object as well
            config.commandAliases = DEFAULT_ALIASES;
            config.manualLobbySetup = DEFAULT_MANUAL_SETUP;
            config.reconnectOnLobbyCommand = DEFAULT_RECONNECT;
            config.lobbyServers = DEFAULT_LOBBY_SERVERS;

        } else {
            Toml toml = new Toml().read(configFile);

            // --- AUTO-UPDATER LOGIC ---
            if (toml.contains("manualLobbySetup") && !toml.contains("settings")) { 
                logger.info("Old config format detected. Updating to new format...");

                boolean oldManualSetup = toml.getBoolean("manualLobbySetup", DEFAULT_MANUAL_SETUP);
                List<String> oldLobbyServers = toml.getList("lobbyServers", DEFAULT_LOBBY_SERVERS);

                File backupFile = dataDirectory.resolve("navigator.toml.old").toFile();
                if (backupFile.exists()) backupFile.delete();
                configFile.renameTo(backupFile);
                
                try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                    writer.println("# A DemonZDevelopment Project");
                    writer.println("#        VelocityNavigator");
                    writer.println("# Your configuration has been automatically updated!");
                    writer.println();
                }

                Map<String, Object> commands = new HashMap<>();
                commands.put("aliases", DEFAULT_ALIASES);

                Map<String, Object> settings = new HashMap<>();
                settings.put("manualLobbySetup", oldManualSetup);
                settings.put("reconnectOnLobbyCommand", DEFAULT_RECONNECT);
                
                Map<String, Object> fullConfig = new HashMap<>();
                fullConfig.put("commands", commands);
                fullConfig.put("settings", settings);
                fullConfig.put("lobbyServers", oldLobbyServers);

                TomlWriter tomlWriter = new TomlWriter();
                tomlWriter.write(fullConfig, new FileWriter(configFile, true));
                
                toml = new Toml().read(configFile);
                logger.info("Config update complete. Old config backed up to navigator.toml.old");
            }
            
            config.commandAliases = toml.getList("commands.aliases", DEFAULT_ALIASES);
            config.manualLobbySetup = toml.getBoolean("settings.manualLobbySetup", DEFAULT_MANUAL_SETUP);
            config.reconnectOnLobbyCommand = toml.getBoolean("settings.reconnectOnLobbyCommand", DEFAULT_RECONNECT);
            config.lobbyServers = toml.getList("lobbyServers", DEFAULT_LOBBY_SERVERS);
        }
        
        return config;
    }
}
