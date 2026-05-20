package com.demonz.velocitynavigator;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "4.0.0",
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
    private final RouteSelectionStrategy selectionStrategy = new RouteSelectionStrategy();
    private final RoutingStats routingStats = new RoutingStats();
    private final DrainService drainService = new DrainService();

    private final Set<String> registeredCommands = new LinkedHashSet<>();

    private ConfigManager configManager;
    private ServerHealthService healthService;
    private LobbyRouter lobbyRouter;
    private RoutePlanner routePlanner;
    private UpdateChecker updateChecker;
    private MetricsService metricsService;
    private CircuitBreaker circuitBreaker;
    private ServerLoadTracker loadTracker;
    private ConsistentHashRing hashRing;
    private PlayerAffinityService affinityService;
    private ConnectionRateTracker rateTracker;
    private GeoRoutingService geoRoutingService;

    private volatile Config config;
    private volatile Config previousConfig;
    private ScheduledTask cacheWarmTask;
    private ScheduledTask purgeTask;
    private ScheduledTask startupUpdateTask;
    private ScheduledTask statsResetTask;

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

            // Wire services into route planner
            routePlanner.setDrainService(drainService);
            if (circuitBreaker != null) {
                routePlanner.setCircuitBreaker(circuitBreaker);
                healthService.setCircuitBreaker(circuitBreaker);
            }
            if (loadTracker != null) {
                routePlanner.setLoadTracker(loadTracker);
                // FIX-1: Wire load tracker into health service so EMA is updated on pings
                healthService.setLoadTracker(loadTracker);
            }
            if (hashRing != null) {
                routePlanner.setHashRing(hashRing);
            }
            if (affinityService != null) {
                routePlanner.setAffinityService(affinityService);
            }
            if (rateTracker != null) {
                routePlanner.setRateTracker(rateTracker);
            }

            this.metricsService = new MetricsService(metricsFactory, this::config, logger);
            this.metricsService.configure(this, config);

            // AC-01: Schedule background cache warming task
            scheduleCacheWarming();

            // AC-03: Schedule cache purge every 60 seconds
            scheduleCachePurge();

            // Schedule stats reset every 60 seconds
            scheduleStatsReset();

            // AC-04: Startup notification - one-time update check
            scheduleStartupUpdateCheck();

            NavigatorAPIProvider.set(this);

            long startupMillis = System.currentTimeMillis() - startedAt;
            logger.info("VelocityNavigator v{} enabled in {}ms.", pluginVersion, startupMillis);
        } catch (IOException exception) {
            logger.error("VelocityNavigator could not start because navigator.toml could not be loaded.", exception);
        } catch (Exception exception) {
            logger.error("VelocityNavigator could not start due to an unexpected error.", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        NavigatorAPIProvider.clear();
        if (cacheWarmTask != null) {
            cacheWarmTask.cancel();
        }
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        if (statsResetTask != null) {
            statsResetTask.cancel();
        }
        // FIX-11: Cancel startup update check if still pending
        if (startupUpdateTask != null) {
            startupUpdateTask.cancel();
        }
        if (healthService != null) {
            healthService.clearCache();
        }
    }

    /**
     * FIX-4: Clean up player affinity on disconnect to prevent memory leaks.
     */
    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        if (affinityService != null) {
            affinityService.removeAffinity(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (config == null || !config.routing().balanceInitialJoin()) {
            return;
        }

        // AC-01: Use cached health data instead of blocking .join()
        Map<String, Integer> cachedServers = healthService.getCachedOnlineServers();
        if (!cachedServers.isEmpty()) {
            RouteDecision decision = routePlanner.plan("", config, cachedServers, event.getPlayer().getUniqueId());
            if (decision.hasSelection()) {
                server.getServer(decision.selectedServer()).ifPresent(target -> {
                    event.setInitialServer(target);
                    if (config.debug().verboseLogging()) {
                        logger.info("[VelocityNavigator] Balanced initial join for {} -> {}",
                                event.getPlayer().getUsername(), decision.selectedServer());
                    }
                });
            }
        }
        // If cache is empty (cold start), fall through to Velocity's built-in try list
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (config == null || !config.notifyAdminsOnJoin()) {
            return;
        }
        if (!event.getPlayer().hasPermission("velocitynavigator.admin")) {
            return;
        }

        UpdateStatus status = updateChecker.status();

        if (status.lastCheckedAt() != null) {
            if (status.updateAvailable()) {
                server.getScheduler().buildTask(this, () -> {
                    event.getPlayer().sendMessage(MessageFormatter.render(
                            "<yellow>[VelocityNavigator]</yellow> <white>Update available: " + pluginVersion
                                    + " → " + status.latestKnownVersion() + ". Use /vn updatecheck for details.</white>"
                    ));
                }).delay(2, TimeUnit.SECONDS).schedule();
            }
        } else {
            // Check hasn't run yet — trigger it and notify when done
            updateChecker.checkAsync(config.updateChecker())
                    .thenRun(() -> {
                        if (updateChecker.status().updateAvailable()) {
                            server.getScheduler().buildTask(this, () -> {
                                if (event.getPlayer().isActive()) {
                                    event.getPlayer().sendMessage(MessageFormatter.render(
                                            "<yellow>[VelocityNavigator]</yellow> <white>Update available: " + pluginVersion
                                                    + " → " + updateChecker.status().latestKnownVersion() + ". Use /vn updatecheck for details.</white>"
                                    ));
                                }
                            }).delay(1, TimeUnit.SECONDS).schedule();
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.debug("VelocityNavigator admin join update check failed: {}", throwable.getMessage());
                        return null;
                    });
        }
    }

    public synchronized ConfigLoadResult reloadConfiguration() throws IOException {
        ConfigLoadResult loadResult = configManager.load();
        applyLoadedConfiguration(loadResult);
        if (metricsService != null) {
            metricsService.configure(this, config);
        }
        // Re-wire services after config change
        if (circuitBreaker != null) {
            healthService.setCircuitBreaker(circuitBreaker);
        }
        // FIX-1: Re-wire load tracker into health service after config reload
        if (loadTracker != null) {
            healthService.setLoadTracker(loadTracker);
        }
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

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    public RoutingStats routingStats() {
        return routingStats;
    }

    public DrainService drainService() {
        return drainService;
    }

    public PlayerAffinityService affinityService() {
        return affinityService;
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public ServerLoadTracker loadTracker() {
        return loadTracker;
    }

    public ConnectionRateTracker rateTracker() {
        return rateTracker;
    }

    public GeoRoutingService geoRoutingService() {
        return geoRoutingService;
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

    @Override
    public Map<String, Long> getRoutingDistribution() {
        return routingStats.getDistribution();
    }

    @Override
    public Map<String, Long> getHealthCheckLatencies() {
        // Placeholder — would need latency tracking in ServerHealthService
        return Map.of();
    }

    @Override
    public Map<String, CircuitBreaker.State> getCircuitBreakerStatuses() {
        if (circuitBreaker == null) {
            return Map.of();
        }
        Map<String, CircuitBreaker.State> statuses = new java.util.LinkedHashMap<>();
        for (String serverName : server.getAllServers().stream()
                .map(rs -> rs.getServerInfo().getName()).toList()) {
            statuses.put(serverName, circuitBreaker.getState(serverName));
        }
        return statuses;
    }

    public Component buildHelpComponent() {
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Admin</bold></gradient>
                <gray>/velocitynavigator reload</gray> <white>Reload navigator.toml</white>
                <gray>/velocitynavigator status</gray> <white>Show runtime status</white>
                <gray>/velocitynavigator version</gray> <white>Show installed and remote version info</white>
                <gray>/velocitynavigator updatecheck</gray> <white>Check Modrinth for updates</white>
                <gray>/velocitynavigator debug player &lt;name&gt;</gray> <white>Preview routing for a player</white>
                <gray>/velocitynavigator debug server &lt;name&gt;</gray> <white>Inspect a server health snapshot</white>
                <gray>/velocitynavigator drain &lt;server&gt;</gray> <white>Drain a server (stop routing to it)</white>
                <gray>/velocitynavigator undrain &lt;server&gt;</gray> <white>Undrain a server (resume routing)</white>
                <gray>/velocitynavigator drain status</gray> <white>Show drained servers</white>
                """);
    }

    public Component buildStatusComponent() {
        UpdateStatus status = updateChecker.status();
        String lastChecked = status.lastCheckedAt() == null ? "never" : status.lastCheckedAt().toString();

        // Routing distribution
        Map<String, Long> distribution = routingStats.getDistribution();
        StringBuilder distBuilder = new StringBuilder();
        if (distribution.isEmpty()) {
            distBuilder.append("No connections recorded yet.");
        } else {
            for (Map.Entry<String, Long> entry : distribution.entrySet()) {
                if (!distBuilder.isEmpty()) distBuilder.append(", ");
                distBuilder.append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }

        // Circuit breaker summary
        String cbStatus = "N/A";
        if (circuitBreaker != null) {
            long open = 0, halfOpen = 0, closed = 0;
            for (String sn : server.getAllServers().stream().map(rs -> rs.getServerInfo().getName()).toList()) {
                switch (circuitBreaker.getState(sn)) {
                    case OPEN -> open++;
                    case HALF_OPEN -> halfOpen++;
                    case CLOSED -> closed++;
                }
            }
            cbStatus = "closed=" + closed + " half_open=" + halfOpen + " open=" + open;
        }

        // Drained servers
        Map<String, Boolean> drainState = drainService.drainState();
        String drainStatus = drainState.isEmpty() ? "None" : String.join(", ", drainState.keySet());

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
                <gray>Circuit breaker:</gray> <white>%s</white>
                <gray>Drained servers:</gray> <white>%s</white>
                <gray>Routing distribution:</gray> <white>%s</white>
                """.formatted(
                pluginVersion,
                config.configVersion(),
                config.routing().selectionMode().configValue(),
                lobbyEntryNames(config.routing().defaultLobbies()),
                config.routing().contextual().enabled(),
                config.healthChecks().enabled(),
                metricsService == null ? "Unavailable" : metricsService.statusLine(),
                config.updateChecker().channel().configValue(),
                lastChecked,
                cbStatus,
                drainStatus,
                distBuilder.toString()
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
                <gray>Selection mode:</gray> <white>%s</white>
                <gray>Reason:</gray> <white>%s</white>
                """.formatted(
                decision.sourceServer().isBlank() ? "none" : decision.sourceServer(),
                decision.requestedGroup(),
                decision.usedGroup(),
                decision.configuredCandidates(),
                decision.onlineCandidates(),
                selected,
                decision.fallbackToDefault(),
                decision.selectionMode().configValue(),
                reason
        ));
    }

    public Component buildServerDebugComponent(ServerHealthService.ServerStatus status) {
        String checkedAt = status.checkedAt() == null ? "never" : status.checkedAt().toString();
        long ageSeconds = status.checkedAt() == null ? -1 : Duration.between(status.checkedAt(), Instant.now()).toSeconds();
        String ageText = ageSeconds < 0 ? "n/a" : ageSeconds + "s ago";

        // Circuit breaker state
        String cbState = "N/A";
        if (circuitBreaker != null) {
            cbState = circuitBreaker.getState(status.serverName()).name();
        }

        String drainState = drainService.isDrained(status.serverName()) ? "DRAINED" : "active";

        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Server Debug</bold></gradient>
                <gray>Server:</gray> <white>%s</white>
                <gray>Registered:</gray> <white>%s</white>
                <gray>Online:</gray> <white>%s</white>
                <gray>Cached:</gray> <white>%s</white>
                <gray>Checked at:</gray> <white>%s</white>
                <gray>Sample age:</gray> <white>%s</white>
                <gray>Players connected:</gray> <white>%s</white>
                <gray>Circuit breaker:</gray> <white>%s</white>
                <gray>Drain status:</gray> <white>%s</white>
                """.formatted(
                status.serverName(),
                status.exists(),
                status.online(),
                status.cached(),
                checkedAt,
                ageText,
                status.playersConnected(),
                cbState,
                drainState
        ));
    }

    private synchronized void applyLoadedConfiguration(ConfigLoadResult loadResult) {
        this.previousConfig = this.config;
        this.config = loadResult.config();
        configManager.logWarnings(loadResult);

        // AC-02: Only reset round-robin when lobby topology changed
        if (previousConfig != null && lobbyTopologyUnchanged(previousConfig, config)) {
            // Lobby topology didn't change, keep round-robin state
        } else {
            selectionStrategy.reset();
        }

        healthService.clearCache();

        // Initialize/update circuit breaker
        Config.CircuitBreakerSettings cbSettings = config.circuitBreaker();
        if (cbSettings.enabled()) {
            this.circuitBreaker = new CircuitBreaker(
                    cbSettings.failureThreshold(),
                    cbSettings.cooldownSeconds(),
                    cbSettings.halfOpenMaxTests()
            );
        } else {
            this.circuitBreaker = null;
        }

        // Initialize load tracker
        this.loadTracker = new ServerLoadTracker(0.3);

        // Initialize hash ring
        this.hashRing = new ConsistentHashRing();

        // Initialize affinity service
        if (config.routing().affinity().enabled()) {
            double stickiness = config.routing().affinity().stickiness();
            if (this.affinityService == null) {
                this.affinityService = new PlayerAffinityService(stickiness);
            } else {
                PlayerAffinityService oldService = this.affinityService;
                this.affinityService = new PlayerAffinityService(stickiness);
                oldService.getAll().forEach(this.affinityService::setAffinity);
            }
        } else {
            this.affinityService = null;
        }

        // Initialize rate tracker
        this.rateTracker = new ConnectionRateTracker(60);

        // Initialize geo routing service (stub)
        this.geoRoutingService = new GeoRoutingService(
                config.geoRouting().enabled(),
                config.geoRouting().databasePath()
        );

        // Wire services into route planner
        if (routePlanner != null) {
            routePlanner.setDrainService(drainService);
            routePlanner.setCircuitBreaker(circuitBreaker);
            routePlanner.setLoadTracker(loadTracker);
            routePlanner.setHashRing(hashRing);
            routePlanner.setAffinityService(affinityService);
            routePlanner.setRateTracker(rateTracker);
        }

        registerCommands();
    }

    private boolean lobbyTopologyUnchanged(Config previous, Config current) {
        // Compare default lobbies
        List<String> prevDefaults = lobbyEntryNames(previous.routing().defaultLobbies());
        List<String> currDefaults = lobbyEntryNames(current.routing().defaultLobbies());
        if (!prevDefaults.equals(currDefaults)) {
            return false;
        }

        // Compare contextual groups
        Set<String> prevGroups = previous.routing().contextual().groups().keySet();
        Set<String> currGroups = current.routing().contextual().groups().keySet();
        if (!prevGroups.equals(currGroups)) {
            return false;
        }

        for (String group : currGroups) {
            Config.GroupConfig prevConfig = previous.routing().contextual().groups().get(group);
            Config.GroupConfig currConfig = current.routing().contextual().groups().get(group);
            if (prevConfig == null || currConfig == null) {
                return false;
            }
            List<String> prevServers = lobbyEntryNames(prevConfig.servers());
            List<String> currServers = lobbyEntryNames(currConfig.servers());
            if (!prevServers.equals(currServers)) {
                return false;
            }
        }

        return true;
    }

    private List<String> lobbyEntryNames(List<Config.LobbyEntry> entries) {
        List<String> names = new ArrayList<>();
        for (Config.LobbyEntry entry : entries) {
            names.add(entry.server());
        }
        return names;
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

    /**
     * AC-01: Schedule background cache warming task.
     */
    private void scheduleCacheWarming() {
        if (cacheWarmTask != null) {
            cacheWarmTask.cancel();
        }
        int intervalSeconds = Math.max(5, config.healthChecks().cacheSeconds());
        cacheWarmTask = server.getScheduler()
                .buildTask(this, () -> {
                    try {
                        lobbyRouter.preview("", config).thenAccept(decision -> {
                            if (config.debug().verboseLogging() && decision.hasSelection()) {
                                logger.debug("[VelocityNavigator] Cache warming: selected {}", decision.selectedServer());
                            }
                        });
                    } catch (Exception e) {
                        logger.debug("[VelocityNavigator] Cache warming failed: {}", e.getMessage());
                    }
                })
                .delay(intervalSeconds, TimeUnit.SECONDS)
                .repeat(intervalSeconds, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * AC-03: Schedule cache purge every 60 seconds.
     */
    private void scheduleCachePurge() {
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        purgeTask = server.getScheduler()
                .buildTask(this, () -> {
                    Duration ttl = Duration.ofSeconds(Math.max(300, config.healthChecks().cacheSeconds() * 5));
                    healthService.purgeExpiredCache(ttl);
                })
                .delay(60, TimeUnit.SECONDS)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Schedule stats reset every 60 seconds.
     */
    private void scheduleStatsReset() {
        if (statsResetTask != null) {
            statsResetTask.cancel();
        }
        statsResetTask = server.getScheduler()
                .buildTask(this, routingStats::reset)
                .delay(60, TimeUnit.SECONDS)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * AC-04: Startup-only update check — one-time task after 5 seconds.
     */
    private void scheduleStartupUpdateCheck() {
        if (!config.notifyOnStartup()) {
            return;
        }
        // FIX-11: Store the task handle so it can be cancelled on shutdown
        this.startupUpdateTask = server.getScheduler()
                .buildTask(this, () -> {
                    updateChecker.checkAsync(config.updateChecker())
                            .thenRun(() -> {
                                if (updateChecker.status().updateAvailable()) {
                                    logger.info("VelocityNavigator update available: {} -> {}",
                                            pluginVersion, updateChecker.status().latestKnownVersion());
                                    logger.info("Download: https://modrinth.com/plugin/velocitynavigator");
                                }
                            })
                            .exceptionally(throwable -> {
                                logger.debug("VelocityNavigator startup update check failed: {}", throwable.getMessage());
                                return null;
                            });
                })
                .delay(5, TimeUnit.SECONDS)
                .schedule();
    }
}
