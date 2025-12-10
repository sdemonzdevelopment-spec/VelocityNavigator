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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "2.2.0-STABLE",
        description = "An intelligent, enterprise-grade lobby system for Velocity.",
        authors = {"DemonZDevelopment"}
)
public class VelocityNavigator {

    public static final String LOBBY_COMMAND_NAME = "lobby";

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Scheduler scheduler;
    private final String pluginVersion;

    @Inject
    public VelocityNavigator(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = Objects.requireNonNull(server, "ProxyServer cannot be null.");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null.");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "DataDirectory cannot be null.");
        this.scheduler = server.getScheduler();
        
        // Safely retrieve version
        String v = "Unknown";
        if (getClass().isAnnotationPresent(Plugin.class)) {
            v = getClass().getAnnotation(Plugin.class).version();
        }
        this.pluginVersion = v;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        long startTime = System.currentTimeMillis();
        
        // 1. Load Configuration
        Config config;
        try {
            config = Config.load(dataDirectory, logger);
        } catch (IOException e) {
            logger.error("CRITICAL: Failed to load configuration. VelocityNavigator will disable itself.", e);
            return;
        }

        // 2. Initialize Modrinth Update Checker (Async)
        // Delayed by 5 seconds to allow network stack to stabilize
        this.scheduler.buildTask(this, () -> {
            new UpdateChecker(logger, pluginVersion, dataDirectory).check();
        })
        .delay(5L, TimeUnit.SECONDS)
        .schedule();

        // 3. Initialize Services
        // FIX: Passed 'logger' as the last argument to match ServerPinger constructor
        ServerPinger serverPinger = new ServerPinger(this, server, config, scheduler, logger);

        // 4. Register Commands
        CommandManager commandManager = server.getCommandManager();
        CommandMeta.Builder lobbyCommandBuilder = commandManager.metaBuilder(LOBBY_COMMAND_NAME);
        
        // Add aliases safely
        List<String> aliases = config.getCommandAliases();
        if (aliases != null && !aliases.isEmpty()) {
            String[] safeAliases = aliases.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
                
            if (safeAliases.length > 0) {
                lobbyCommandBuilder.aliases(safeAliases);
            }
        }

        CommandMeta lobbyCommandMeta = lobbyCommandBuilder.build();
        
        // FIX: Passed 'logger' as the last argument to match LobbyCommand constructor
        commandManager.register(lobbyCommandMeta, new LobbyCommand(server, config, serverPinger, logger));

        long duration = System.currentTimeMillis() - startTime;
        logger.info("VelocityNavigator v{} enabled in {}ms. (Modrinth Ready)", pluginVersion, duration);
    }
}