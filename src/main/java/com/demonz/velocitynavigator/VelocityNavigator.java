package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import org.slf4j.Logger;
import javax.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "2.0.0-BETA", // Version bumped to reflect the fix
        description = "An intelligent, configurable lobby command for Velocity.",
        authors = {"DemonZDevelopment"}
)
public class VelocityNavigator {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Scheduler scheduler; // This field stays the same
    private final String pluginVersion;

    @Inject
    public VelocityNavigator(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        // Get the scheduler from the server object - this is the correct way
        this.scheduler = server.getScheduler(); 
        this.pluginVersion = getClass().getAnnotation(Plugin.class).version();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Config config;
        try {
            config = Config.load(dataDirectory, logger);
        } catch (IOException e) {
            logger.error("Failed to load or create configuration! The plugin will not function.", e);
            return;
        }

        new UpdateChecker(logger, pluginVersion, dataDirectory).check();

        ServerPinger serverPinger = new ServerPinger(server, config, scheduler);

        CommandManager commandManager = server.getCommandManager();
        CommandMeta.Builder lobbyCommandBuilder = commandManager.metaBuilder("lobby");
        
        List<String> aliases = config.getCommandAliases();
        if (aliases != null && !aliases.isEmpty()) {
            lobbyCommandBuilder.aliases(aliases.toArray(new String[0]));
        }

        CommandMeta lobbyCommandMeta = lobbyCommandBuilder.build();
        commandManager.register(lobbyCommandMeta, new LobbyCommand(server, config, serverPinger));

        logger.info("A DemonZDevelopment Project - VelocityNavigator v{} has been enabled successfully!", pluginVersion);
    }
}
