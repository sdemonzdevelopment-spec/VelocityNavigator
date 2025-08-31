package com.example.velocitynavigator;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class Config {

    private boolean manualLobbySetup;
    private List<String> lobbyServers;

    // Default values for when the file is first created
    private static final boolean DEFAULT_MANUAL_SETUP = false;
    private static final List<String> DEFAULT_LOBBY_SERVERS = Arrays.asList("lobby1", "lobby2", "lobby3");

    public boolean isManualLobbySetup() {
        return manualLobbySetup;
    }

    public List<String> getLobbyServers() {
        return lobbyServers;
    }

    /**
     * Loads the configuration from the given path, creating it with defaults if it doesn't exist.
     * @param dataDirectory The plugin's data directory.
     * @return A loaded Config object.
     * @throws IOException If the file cannot be read or written.
     */
    public static Config load(Path dataDirectory) throws IOException {
        File configFile = dataDirectory.resolve("navigator.toml").toFile();
        
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        Config config = new Config();
        
        if (!configFile.exists()) {
            // Set defaults and write the new file
            config.manualLobbySetup = DEFAULT_MANUAL_SETUP;
            config.lobbyServers = DEFAULT_LOBBY_SERVERS;
            
            TomlWriter tomlWriter = new TomlWriter();
            tomlWriter.write(config, configFile);
        } else {
            // Load from existing file
            Toml toml = new Toml().read(configFile);
            config.manualLobbySetup = toml.getBoolean("manualLobbySetup", DEFAULT_MANUAL_SETUP);
            config.lobbyServers = toml.getList("lobbyServers", DEFAULT_LOBBY_SERVERS);
        }
        
        return config;
    }
}
