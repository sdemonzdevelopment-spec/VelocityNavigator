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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {

    private transient List<String> commandAliases;
    private transient boolean manualLobbySetup;
    private transient boolean reconnectOnLobbyCommand;
    private transient List<String> lobbyServers;

    private static final List<String> DEFAULT_ALIASES = Arrays.asList("hub", "spawn");
    private static final boolean DEFAULT_MANUAL_SETUP = false;
    private static final boolean DEFAULT_RECONNECT = true;
    private static final List<String> DEFAULT_LOBBY_SERVERS = Arrays.asList("lobby1", "lobby2", "lobby3");

    public List<String> getCommandAliases() { return commandAliases; }
    public boolean isManualLobbySetup() { return manualLobbySetup; }
    public boolean isReconnectOnLobbyCommand() { return reconnectOnLobbyCommand; }
    public List<String> getLobbyServers() { return lobbyServers; }

    public static Config load(Path dataDirectory, Logger logger) throws IOException {
        File configFile = dataDirectory.resolve("navigator.toml").toFile();
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        Config config = new Config();
        TomlWriter tomlWriter = new TomlWriter();

        if (!configFile.exists()) {
            logger.info("No config file found, creating a new one...");
            
            // --- CORRECTED FILE WRITING LOGIC (FOR NEW FILES) ---
            String header = "# A DemonZDevelopment Project\n#        VelocityNavigator\n\n";
            
            Map<String, Object> commands = new HashMap<>();
            commands.put("aliases", DEFAULT_ALIASES);
            Map<String, Object> settings = new HashMap<>();
            settings.put("manualLobbySetup", DEFAULT_MANUAL_SETUP);
            settings.put("reconnectOnLobbyCommand", DEFAULT_RECONNECT);
            Map<String, Object> fullConfigMap = new HashMap<>();
            fullConfigMap.put("commands", commands);
            fullConfigMap.put("settings", settings);
            fullConfigMap.put("lobbyServers", DEFAULT_LOBBY_SERVERS);

            String tomlString = tomlWriter.write(fullConfigMap);

            try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                writer.print(header + tomlString);
            }
            
            config.commandAliases = DEFAULT_ALIASES;
            config.manualLobbySetup = DEFAULT_MANUAL_SETUP;
            config.reconnectOnLobbyCommand = DEFAULT_RECONNECT;
            config.lobbyServers = DEFAULT_LOBBY_SERVERS;

        } else {
            Toml toml = new Toml().read(configFile);

            if (toml.contains("manualLobbySetup") && !toml.contains("settings")) {
                logger.info("Old config format detected. Updating to new format...");

                boolean oldManualSetup = toml.getBoolean("manualLobbySetup", DEFAULT_MANUAL_SETUP);
                List<String> oldLobbyServers = toml.getList("lobbyServers", DEFAULT_LOBBY_SERVERS);

                File backupFile = dataDirectory.resolve("navigator.toml.old").toFile();
                if (backupFile.exists()) backupFile.delete();
                configFile.renameTo(backupFile);
                
                // --- CORRECTED FILE WRITING LOGIC (FOR UPDATING) ---
                String header = "# A DemonZDevelopment Project\n#        VelocityNavigator\n# Your configuration has been automatically updated!\n\n";

                Map<String, Object> commands = new HashMap<>();
                commands.put("aliases", DEFAULT_ALIASES);
                Map<String, Object> settings = new HashMap<>();
                settings.put("manualLobbySetup", oldManualSetup);
                settings.put("reconnectOnLobbyCommand", DEFAULT_RECONNECT);
                Map<String, Object> fullConfigMap = new HashMap<>();
                fullConfigMap.put("commands", commands);
                fullConfigMap.put("settings", settings);
                fullConfigMap.put("lobbyServers", oldLobbyServers);

                String tomlString = tomlWriter.write(fullConfigMap);
                
                try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                    writer.print(header + tomlString);
                }
                
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
