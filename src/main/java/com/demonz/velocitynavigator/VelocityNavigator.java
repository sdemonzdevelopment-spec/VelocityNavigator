package com.demonz.velocitynavigator;

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
import java.util.List;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "2.0-RELEASE",
        description = "An advanced, configurable lobby command for Velocity.",
        authors = {"DemonZDevelopment"}
)
public class VelocityNavigator {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final String pluginVersion;
    private Config config;

    @Inject
    public VelocityNavigator(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.pluginVersion = getClass().getAnnotation(Plugin.class).version();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            config = Config.load(dataDirectory, logger);
        } catch (IOException e) {
            logger.error("Failed to load or create configuration! The plugin will not function.", e);
            return;
        }

        // The update checker is now non-configurable and runs automatically.
        new UpdateChecker(logger, pluginVersion, dataDirectory).check();

        CommandManager commandManager = server.getCommandManager();
        CommandMeta.Builder lobbyCommandBuilder = commandManager.metaBuilder("lobby");
        
        List<String> aliases = config.getCommandAliases();
        if (aliases != null && !aliases.isEmpty()) {
            lobbyCommandBuilder.aliases(aliases.toArray(new String[0]));
        }

        CommandMeta lobbyCommandMeta = lobbyCommandBuilder.build();
        commandManager.register(lobbyCommandMeta, new LobbyCommand(server, config));

        logger.info("A DemonZDevelopment Project - VelocityNavigator v{} has been enabled successfully!", pluginVersion);
    }
}