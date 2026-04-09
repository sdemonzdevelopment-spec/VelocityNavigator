package com.demonz.velocitynavigator;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "3.0.0",
        description = "Premium lobby navigation and diagnostics for Velocity proxies.",
        authors = {"DemonZDevelopment"}
)
public final class VelocityNavigator implements NavigatorAPI {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private final String pluginVersion;
    private final CooldownService cooldownService = new CooldownService();
    private final RouteSelectionStrategy selectionStrategy = new RouteSelectionStrategy(new Random());

    private final Set<String> registeredCommands = new LinkedHashSet<>();

    private ConfigManager configManager;
    private ServerHealthService healthService;
    private LobbyRouter lobbyRouter;
    private RoutePlanner routePlanner;
    private UpdateChecker updateChecker;
    private MetricsService metricsService;
    private Config config;
    private ScheduledTask updateTask;

    @Inject
    public VelocityNavigator(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.metricsFactory = Objects.requireNonNull(metricsFactory, "metricsFactory");
        Plugin annotation = getClass().getAnnotation(Plugin.class);
        this.pluginVersion = annotation == null ? "unknown" : annotation.version();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        long startedAt = System.currentTimeMillis();
        try {
            this.configManager = new ConfigManager(dataDirectory, logger);
            this.healthService = new ServerHealthService(server, logger);
            this.routePlanner = new RoutePlanner(selectionStrategy);
            this.lobbyRouter = new LobbyRouter(healthService, routePlanner);
            this.updateChecker = new UpdateChecker(logger, pluginVersion);

            ConfigLoadResult loadResult = configManager.load();
            applyLoadedConfiguration(loadResult);
            this.metricsService = new MetricsService(metricsFactory, this::config, logger);
            this.metricsService.configure(this, config);
            scheduleUpdateCheck();
            NavigatorAPIProvider.set(this);

            long startupMillis = System.currentTimeMillis() - startedAt;
            logger.info("VelocityNavigator v{} enabled in {}ms.", pluginVersion, startupMillis);
        } catch (IOException exception) {
            logger.error("VelocityNavigator could not start because navigator.toml could not be loaded.", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        NavigatorAPIProvider.clear();
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (healthService != null) {
            healthService.clearCache();
        }
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (config == null || !config.routing().balanceInitialJoin()) {
            return;
        }
        lobbyRouter.preview("", config).thenAccept(decision -> {
            if (decision.hasSelection()) {
                server.getServer(decision.selectedServer()).ifPresent(target -> {
                    event.setInitialServer(target);
                    if (config.debug().verboseLogging()) {
                        logger.info("[VelocityNavigator] Balanced initial join for {} -> {}",
                                event.getPlayer().getUsername(), decision.selectedServer());
                    }
                });
            }
        }).join(); // Must block — Velocity needs the result before the event completes
    }

    public synchronized ConfigLoadResult reloadConfiguration() throws IOException {
        ConfigLoadResult loadResult = configManager.load();
        applyLoadedConfiguration(loadResult);
        if (metricsService != null) {
            metricsService.configure(this, config);
        }
        scheduleUpdateCheck();
        return loadResult;
    }

    public ProxyServer server() {
        return server;
    }

    public Logger logger() {
        return logger;
    }

    public Config config() {
        return config;
    }

    public CooldownService cooldowns() {
        return cooldownService;
    }

    public CompletableFuture<RouteDecision> previewRoute(Player player) {
        return lobbyRouter.preview(player, config);
    }

    public CompletableFuture<ServerHealthService.ServerStatus> inspectServer(String name) {
        return healthService.inspectServer(name, config.healthChecks());
    }

    @Override
    public Config.Routing getRoutingConfig() {
        return config.routing();
    }

    @Override
    public Config.SelectionMode getSelectionMode() {
        return config.routing().selectionMode();
    }

    public Component buildHelpComponent() {
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Admin</bold></gradient>
                <gray>/velocitynavigator reload</gray> <white>Reload navigator.toml</white>
                <gray>/velocitynavigator status</gray> <white>Show runtime status</white>
                <gray>/velocitynavigator version</gray> <white>Show installed and remote version info</white>
                <gray>/velocitynavigator debug player &lt;name&gt;</gray> <white>Preview routing for a player</white>
                <gray>/velocitynavigator debug server &lt;name&gt;</gray> <white>Inspect a server health snapshot</white>
                """);
    }

    public Component buildStatusComponent() {
        UpdateStatus status = updateChecker.status();
        String lastChecked = status.lastCheckedAt() == null ? "never" : status.lastCheckedAt().toString();
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Status</bold></gradient>
                <gray>Plugin version:</gray> <white>%s</white>
                <gray>Config version:</gray> <white>%s</white>
                <gray>Routing mode:</gray> <white>%s</white>
                <gray>Default lobbies:</gray> <white>%s</white>
                <gray>Contextual routing:</gray> <white>%s</white>
                <gray>Health checks:</gray> <white>%s</white>
                <gray>bStats:</gray> <white>%s</white>
                <gray>Update checker:</gray> <white>%s (last check: %s)</white>
                """.formatted(
                pluginVersion,
                config.configVersion(),
                config.routing().selectionMode().configValue(),
                config.routing().defaultLobbies(),
                config.routing().contextual().enabled(),
                config.healthChecks().enabled(),
                metricsService == null ? "Unavailable" : metricsService.statusLine(),
                config.updateChecker().channel().configValue(),
                lastChecked
        ));
    }

    public Component buildVersionComponent() {
        UpdateStatus status = updateChecker.status();
        String remote = status.latestKnownVersion();
        String availability = status.updateAvailable() ? "<green>Update available</green>" : "<gray>No newer version found</gray>";
        String errorLine = status.lastError().isBlank() ? "<gray>No update-check errors recorded.</gray>" : "<red>" + status.lastError() + "</red>";
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Version</bold></gradient>
                <gray>Installed:</gray> <white>%s</white>
                <gray>Latest allowed remote:</gray> <white>%s</white>
                %s
                %s
                """.formatted(pluginVersion, remote, availability, errorLine));
    }

    public Component buildPlayerDebugComponent(RouteDecision decision) {
        String selected = decision.hasSelection() ? decision.selectedServer() : "none";
        String reason = decision.reason() == null || decision.reason().isBlank() ? "none" : decision.reason();
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Route Debug</bold></gradient>
                <gray>Source server:</gray> <white>%s</white>
                <gray>Requested group:</gray> <white>%s</white>
                <gray>Used group:</gray> <white>%s</white>
                <gray>Configured candidates:</gray> <white>%s</white>
                <gray>Online candidates:</gray> <white>%s</white>
                <gray>Selected server:</gray> <white>%s</white>
                <gray>Fallback to default:</gray> <white>%s</white>
                <gray>Reason:</gray> <white>%s</white>
                """.formatted(
                decision.sourceServer().isBlank() ? "none" : decision.sourceServer(),
                decision.requestedGroup(),
                decision.usedGroup(),
                decision.configuredCandidates(),
                decision.onlineCandidates(),
                selected,
                decision.fallbackToDefault(),
                reason
        ));
    }

    public Component buildServerDebugComponent(ServerHealthService.ServerStatus status) {
        String checkedAt = status.checkedAt() == null ? "never" : status.checkedAt().toString();
        long ageSeconds = status.checkedAt() == null ? -1 : Duration.between(status.checkedAt(), Instant.now()).toSeconds();
        String ageText = ageSeconds < 0 ? "n/a" : ageSeconds + "s ago";
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Server Debug</bold></gradient>
                <gray>Server:</gray> <white>%s</white>
                <gray>Registered:</gray> <white>%s</white>
                <gray>Online:</gray> <white>%s</white>
                <gray>Cached:</gray> <white>%s</white>
                <gray>Checked at:</gray> <white>%s</white>
                <gray>Sample age:</gray> <white>%s</white>
                <gray>Players connected:</gray> <white>%s</white>
                """.formatted(
                status.serverName(),
                status.exists(),
                status.online(),
                status.cached(),
                checkedAt,
                ageText,
                status.playersConnected()
        ));
    }

    private synchronized void applyLoadedConfiguration(ConfigLoadResult loadResult) {
        this.config = loadResult.config();
        configManager.logWarnings(loadResult);
        selectionStrategy.reset();
        healthService.clearCache();
        registerCommands();
    }

    private synchronized void registerCommands() {
        CommandManager commandManager = server.getCommandManager();
        unregisterCommands(commandManager);

        LobbyCommand lobbyCommand = new LobbyCommand(this);
        CommandMeta.Builder lobbyMetaBuilder = commandManager.metaBuilder(config.commands().primary());
        if (!config.commands().aliases().isEmpty()) {
            lobbyMetaBuilder.aliases(config.commands().aliases().toArray(String[]::new));
        }
        commandManager.register(lobbyMetaBuilder.build(), lobbyCommand);
        registeredCommands.add(config.commands().primary());
        registeredCommands.addAll(config.commands().aliases());

        List<String> adminNames = new ArrayList<>(config.commands().adminAliases());
        String adminPrimary = adminNames.get(0);
        String[] adminAliases = adminNames.subList(1, adminNames.size()).toArray(String[]::new);
        NavigatorAdminCommand adminCommand = new NavigatorAdminCommand(this);
        CommandMeta.Builder adminMetaBuilder = commandManager.metaBuilder(adminPrimary);
        if (adminAliases.length > 0) {
            adminMetaBuilder.aliases(adminAliases);
        }
        commandManager.register(adminMetaBuilder.build(), adminCommand);
        registeredCommands.add(adminPrimary);
        registeredCommands.addAll(adminNames.subList(1, adminNames.size()));
    }

    private void unregisterCommands(CommandManager commandManager) {
        for (String command : registeredCommands) {
            commandManager.unregister(command);
        }
        registeredCommands.clear();
    }

    private void scheduleUpdateCheck() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (!config.updateChecker().enabled()) {
            return;
        }
        updateTask = server.getScheduler()
                .buildTask(this, () -> updateChecker.checkAsync(config.updateChecker()))
                .delay(config.updateChecker().startupDelaySeconds(), TimeUnit.SECONDS)
                .schedule();
    }
}
