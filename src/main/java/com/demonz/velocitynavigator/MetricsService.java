package com.demonz.velocitynavigator;

import org.bstats.charts.SimplePie;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

public final class MetricsService {

    private static final int BSTATS_PLUGIN_ID = 28341;

    private final Metrics.Factory metricsFactory;
    private final Supplier<Config> configSupplier;
    private final Logger logger;

    private Metrics metrics;
    private boolean active;
    private String statusLine = "Disabled";

    public MetricsService(Metrics.Factory metricsFactory, Supplier<Config> configSupplier, Logger logger) {
        this.metricsFactory = Objects.requireNonNull(metricsFactory, "metricsFactory");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void configure(VelocityNavigator plugin, Config config) {
        active = metrics != null;

        if (!config.metrics().enabled()) {
            statusLine = active ? "Active (disabled in config after startup)" : "Disabled in config";
            return;
        }

        if (metrics != null) {
            statusLine = "Active";
            active = true;
            return;
        }

        metrics = metricsFactory.make(plugin, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("selection_mode", () -> safeGet(() -> currentConfig().routing().selectionMode().configValue(), "unknown")));
        metrics.addCustomChart(new SimplePie("contextual_routing_enabled", () -> safeGet(() -> Boolean.toString(currentConfig().routing().contextual().enabled()), "false")));
        metrics.addCustomChart(new SimplePie("health_checks_enabled", () -> safeGet(() -> Boolean.toString(currentConfig().healthChecks().enabled()), "false")));
        metrics.addCustomChart(new SimplePie("default_lobby_bucket", this::lobbyBucket));
        metrics.addCustomChart(new SimplePie("circuit_breaker_enabled", () -> safeGet(() -> Boolean.toString(currentConfig().circuitBreaker().enabled()), "false")));
        active = true;
        statusLine = "Active";
    }

    public boolean active() {
        return active;
    }

    public String statusLine() {
        return statusLine;
    }

    private Config currentConfig() {
        return configSupplier.get();
    }

    private String lobbyBucket() {
        try {
            var lobbies = currentConfig().routing().defaultLobbies();
            int size = lobbies == null ? 0 : lobbies.size();
            if (size <= 0) {
                return "0";
            }
            if (size == 1) {
                return "1";
            }
            if (size <= 3) {
                return "2-3";
            }
            if (size <= 6) {
                return "4-6";
            }
            return "7+";
        } catch (Exception e) {
            return "0";
        }
    }

    private String safeGet(java.util.function.Supplier<String> supplier, String fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return fallback;
        }
    }
}
