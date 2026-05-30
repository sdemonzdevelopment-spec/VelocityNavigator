/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "4.2.0",
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
    private final java.util.concurrent.atomic.AtomicLong playerJoins = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong playerLeaves = new java.util.concurrent.atomic.AtomicLong(0);

    private final Set<String> registeredCommands = new LinkedHashSet<>();
    private final ConcurrentMap<UUID, MenuSession> menuSessions = new ConcurrentHashMap<>();

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
    private BedrockHandler bedrockHandler;
    private PrometheusExporter prometheusExporter;

    private volatile Config config;
    private volatile Config previousConfig;
    private ScheduledTask cacheWarmTask;
    private ScheduledTask purgeTask;
    private ScheduledTask startupUpdateTask;

    @Inject
    public VelocityNavigator(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.metricsFactory = metricsFactory;
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
            this.prometheusExporter = new PrometheusExporter(this);

            ConfigLoadResult loadResult = configManager.load();
            applyLoadedConfiguration(loadResult);

            this.bedrockHandler = new BedrockHandler(server);
            if (this.bedrockHandler.isBedrockSupported(config)) {
                logger.info("[VelocityNavigator] Bedrock/Geyser support: enabled (auto-detected)");
            } else {
                logger.info("[VelocityNavigator] Bedrock/Geyser support: disabled");
            }

            if (config != null && config.startup() != null) {
                FirstRunHandler.checkAndShowWelcome(logger, dataDirectory, pluginVersion, config.startup().welcomeEnabled(), config.startup().wikiUrl());
            }

            if (metricsFactory != null) {
                this.metricsService = new MetricsService(metricsFactory, this::config, logger);
                this.metricsService.configure(this, config);
            }

            scheduleCacheWarming();

            scheduleCachePurge();

            NavigatorAPIProvider.set(this);

            long startupMillis = System.currentTimeMillis() - startedAt;
            logger.info("VelocityNavigator v{} enabled in {}ms.", pluginVersion, startupMillis);
            logger.info("[VelocityNavigator] We would love to hear your feedback! Join our Discord: https://discord.com/invite/GYsTt96ypf");
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
        if (startupUpdateTask != null) {
            startupUpdateTask.cancel();
        }
        if (healthService != null) {
            healthService.clearCache();
        }
        if (prometheusExporter != null) {
            prometheusExporter.stop();
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        playerLeaves.incrementAndGet();
        menuSessions.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (config == null || !config.routing().balanceInitialJoin()) {
            return;
        }

        Map<String, Integer> routeableServers = healthService.getCachedOnlineServers();
        if (routeableServers.isEmpty()) {
            routeableServers = healthService.getRegisteredOnlineServers(configuredLobbyServerNames(config));
        }

        UUID affinityUuid = event.getPlayer().getUniqueId();
        if (bedrockHandler != null && bedrockHandler.isBedrockSupported(config) && config.bedrock().affinityUseJavaUuid()) {
            if (bedrockHandler.isBedrockPlayer(event.getPlayer(), config)) {
                affinityUuid = FloodgateIntegration.getJavaUUID(event.getPlayer());
            }
        }
        RouteDecision decision = routePlanner.plan("", config, routeableServers, affinityUuid);
        if (!decision.hasSelection()) {
            disconnectInitialJoin(event, decision);
            return;
        }

        server.getServer(decision.selectedServer()).ifPresentOrElse(target -> {
            event.setInitialServer(target);
            routingStats.recordRedirect("initial_join", decision.selectedServer());
            if (rateTracker != null) {
                rateTracker.recordConnection(decision.selectedServer());
            }
            if (config.debug().verboseLogging()) {
                logger.info("[VelocityNavigator] Balanced initial join for {} -> {}",
                        event.getPlayer().getUsername(), decision.selectedServer());
            }
        }, () -> disconnectInitialJoin(event, decision));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        playerJoins.incrementAndGet();
        if (config == null || updateChecker == null || !config.notifyAdminsOnJoin() || !config.updateChecker().notifyAdmins()) {
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
        if (loadTracker != null) {
            healthService.setLoadTracker(loadTracker);
        }
        scheduleCacheWarming();
        scheduleCachePurge();
        return loadResult;
    }

    public java.nio.file.Path getDataDirectory() {
        return dataDirectory;
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

    public BedrockHandler bedrockHandler() {
        return bedrockHandler;
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

    public ServerHealthService healthService() {
        return healthService;
    }

    public String createMenuToken(Player player, List<String> serverNames) {
        Set<String> allowedServers = new LinkedHashSet<>();
        if (serverNames != null) {
            for (String serverName : serverNames) {
                if (serverName != null && !serverName.isBlank()) {
                    allowedServers.add(serverName.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        menuSessions.put(player.getUniqueId(), new MenuSession(token, Set.copyOf(allowedServers), Instant.now().plusSeconds(60)));
        return token;
    }

    public boolean consumeMenuToken(Player player, String targetServer, String token) {
        if (player == null || targetServer == null || token == null || token.isBlank()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        String normalizedTarget = targetServer.toLowerCase(Locale.ROOT);
        java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean(false);
        menuSessions.compute(playerId, (id, session) -> {
            if (session == null) {
                return null;
            }
            Instant now = Instant.now();
            if (now.isAfter(session.expiresAt())) {
                return null;
            }
            if (!session.token().equals(token) || !session.allowedServers().contains(normalizedTarget)) {
                return session;
            }
            consumed.set(true);
            return null;
        });
        return consumed.get();
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
        if (healthService == null) {
            return Map.of();
        }
        return healthService.getLatencies();
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

    public long getPlayerJoins() {
        return playerJoins.get();
    }

    public long getPlayerLeaves() {
        return playerLeaves.get();
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
                <gray>/velocitynavigator servers</gray> <white>Show all lobby server statuses</white>
                <gray>/velocitynavigator setup grafana</gray> <white>Generate the Grafana diagnostics dashboard</white>
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

        if (previousConfig != null && lobbyTopologyUnchanged(previousConfig, config)) {
            // Lobby topology didn't change, keep round-robin state
        } else {
            selectionStrategy.reset();
        }

        healthService.clearCache();

        // Initialize/update circuit breaker
        Config.CircuitBreakerSettings cbSettings = config.circuitBreaker();
        if (cbSettings.enabled()) {
            if (previousConfig == null || !previousConfig.circuitBreaker().equals(cbSettings) || this.circuitBreaker == null) {
                this.circuitBreaker = new CircuitBreaker(
                        cbSettings.failureThreshold(),
                        cbSettings.cooldownSeconds(),
                        cbSettings.halfOpenMaxTests()
                );
            }
        } else {
            this.circuitBreaker = null;
        }

        // Initialize load tracker
        if (this.loadTracker == null) {
            this.loadTracker = new ServerLoadTracker(0.3);
        }

        // Initialize hash ring
        if (this.hashRing == null) {
            this.hashRing = new ConsistentHashRing();
        }

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
        if (this.rateTracker == null) {
            this.rateTracker = new ConnectionRateTracker(60);
        }
        this.rateTracker.retainServers(configuredLobbyServerNames(config));

        // Initialize geo routing service (stub)
        this.geoRoutingService = new GeoRoutingService(
                config.geoRouting().enabled(),
                config.geoRouting().databasePath()
        );
        if (config.geoRouting().enabled()) {
            logger.warn("[VelocityNavigator] geo_routing.enabled is true, but geo routing is not implemented in this build. Location data will not affect routing.");
        }

        // Wire services into route planner and health service
        if (routePlanner != null) {
            routePlanner.setDrainService(drainService);
            routePlanner.setCircuitBreaker(circuitBreaker);
            routePlanner.setLoadTracker(loadTracker);
            routePlanner.setHashRing(hashRing);
            routePlanner.setAffinityService(affinityService);
            routePlanner.setRateTracker(rateTracker);
            routePlanner.setHealthService(healthService);
        }
        if (healthService != null) {
            healthService.setCircuitBreaker(circuitBreaker);
            healthService.setLoadTracker(loadTracker);
        }

        registerCommands();
        if (prometheusExporter != null) {
            prometheusExporter.start(config.metrics().prometheus());
        }
        schedulePeriodicUpdateCheck();
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

    private Set<String> configuredLobbyServerNames(Config currentConfig) {
        Set<String> names = new LinkedHashSet<>();
        if (currentConfig == null || currentConfig.routing() == null) {
            return names;
        }
        addLobbyNames(names, currentConfig.routing().defaultLobbies());
        if (currentConfig.lobbyFallback() != null
                && "fallback_server".equalsIgnoreCase(currentConfig.lobbyFallback().noServerStrategy())
                && !currentConfig.lobbyFallback().fallbackServer().isBlank()) {
            names.add(currentConfig.lobbyFallback().fallbackServer());
        }
        Config.Contextual contextual = currentConfig.routing().contextual();
        if (contextual != null && contextual.groups() != null) {
            for (Config.GroupConfig groupConfig : contextual.groups().values()) {
                if (groupConfig != null) {
                    addLobbyNames(names, groupConfig.servers());
                }
            }
        }
        return names;
    }

    private void addLobbyNames(Set<String> names, List<Config.LobbyEntry> entries) {
        if (entries == null) {
            return;
        }
        for (Config.LobbyEntry entry : entries) {
            if (entry != null && entry.server() != null && !entry.server().isBlank()) {
                names.add(entry.server());
            }
        }
    }

    private void disconnectInitialJoin(PlayerChooseInitialServerEvent event, RouteDecision decision) {
        String reason = decision == null || decision.reason() == null || decision.reason().isBlank()
                ? "No lobby servers are currently available."
                : decision.reason();
        String mode = decision == null || decision.selectionMode() == null
                ? config.routing().selectionMode().configValue()
                : decision.selectionMode().configValue();
        String message = config.lobbyFallback() == null
                ? config.messages().noLobbyFound()
                : config.lobbyFallback().noServerMessage();
        event.getPlayer().disconnect(MessageFormatter.render(
                message,
                Map.of(
                        "reason", reason,
                        "mode", mode,
                        "player", event.getPlayer().getUsername()
                ),
                event.getPlayer()
        ));
        if (config.debug().verboseLogging()) {
            logger.info("[VelocityNavigator] Initial join for {} denied: {}",
                    event.getPlayer().getUsername(), reason);
        }
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
        if (adminNames.isEmpty()) {
            adminNames.add("velocitynavigator");
            adminNames.add("vn");
        }
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

    private void scheduleCacheWarming() {
        if (cacheWarmTask != null) {
            cacheWarmTask.cancel();
            cacheWarmTask = null;
        }
        if (config.healthChecks().cacheSeconds() <= 0) {
            return; // Caching is disabled, no warming needed
        }
        int intervalSeconds = Math.max(5, config.healthChecks().cacheSeconds());
        warmHealthCache();
        cacheWarmTask = server.getScheduler()
                .buildTask(this, this::warmHealthCache)
                .delay(intervalSeconds, TimeUnit.SECONDS)
                .repeat(intervalSeconds, TimeUnit.SECONDS)
                .schedule();
    }

    private void warmHealthCache() {
        try {
            lobbyRouter.preview("", config).thenAccept(decision -> {
                if (config.debug().verboseLogging() && decision.hasSelection()) {
                    logger.debug("[VelocityNavigator] Cache warming: selected {}", decision.selectedServer());
                }
            }).exceptionally(throwable -> {
                logger.debug("[VelocityNavigator] Cache warming failed: {}", throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.debug("[VelocityNavigator] Cache warming failed: {}", e.getMessage());
        }
    }

    private void scheduleCachePurge() {
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        purgeTask = server.getScheduler()
                .buildTask(this, () -> {
                    Duration ttl = Duration.ofSeconds(Math.max(300, config.healthChecks().cacheSeconds() * 5));
                    healthService.purgeExpiredCache(ttl);
                    if (affinityService != null) {
                        affinityService.purgeExpired();
                    }
                    if (rateTracker != null) {
                        rateTracker.retainServers(configuredLobbyServerNames(config));
                        rateTracker.purge();
                    }
                })
                .delay(60, TimeUnit.SECONDS)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Periodic update checker task that respects configured interval and enabled status.
     */
    private void schedulePeriodicUpdateCheck() {
        if (config == null || config.updateChecker() == null) {
            return;
        }
        if (startupUpdateTask != null) {
            startupUpdateTask.cancel();
        }
        if (!config.updateChecker().enabled()) {
            return;
        }
        int intervalMinutes = Math.max(30, config.updateChecker().checkIntervalMinutes());
        var taskBuilder = server.getScheduler()
                .buildTask(this, () -> {
                    updateChecker.checkAsync(config.updateChecker());
                })
                .repeat(intervalMinutes, TimeUnit.MINUTES);
        this.startupUpdateTask = config.notifyOnStartup()
                ? taskBuilder.delay(5, TimeUnit.SECONDS).schedule()
                : taskBuilder.delay(intervalMinutes, TimeUnit.MINUTES).schedule();
    }

    private record MenuSession(String token, Set<String> allowedServers, Instant expiresAt) {
    }
}
