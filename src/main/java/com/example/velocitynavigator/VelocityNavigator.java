package com.example.velocitynavigator;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "1.0-SNAPSHOT",
        description = "A configurable lobby command for Velocity.",
        authors = {"YourName"}
)
public class VelocityNavigator {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private Config config;

    @Inject
    public VelocityNavigator(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Load configuration
        try {
            config = Config.load(dataDirectory);
        } catch (IOException e) {
            logger.error("Failed to load configuration! The plugin will not function.", e);
            return;
        }

        // Register command
        CommandManager commandManager = server.getCommandManager();
        CommandMeta lobbyCommandMeta = commandManager.metaBuilder("lobby")
                .aliases("hub", "spawn")
                .build();
        
        commandManager.register(lobbyCommandMeta, new LobbyCommand(server, config));

        logger.info("VelocityNavigator has been enabled successfully!");
    }
}
