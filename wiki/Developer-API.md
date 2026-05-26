# Developer API Hooking

> Integrate deeply with the VelocityNavigator backbone from your own private Velocity proxy plugins and game modes!

---

## Dependency Injector

Add VelocityNavigator as a Maven compilation dependency in your core `pom.xml`:

```xml
<dependency>
    <groupId>com.demonz</groupId>
    <artifactId>VelocityNavigator</artifactId>
    <version>4.1.0</version>
    <scope>provided</scope>
</dependency>
```

> [!WARNING]  
> **Always use `<scope>provided</scope>`!** VelocityNavigator will already be statically loaded on the proxy classloader at runtime. Overcompiling it into your custom jar will cause extreme classpath exception explosions.

Enforce boot order by tagging your velocity plugin class:

```java
@Plugin(
    id = "your-custom-router",
    dependencies = { @Dependency(id = "velocitynavigator") }
)
```

---

## Memory Retrieval Pipeline

```java
import com.demonz.velocitynavigator.NavigatorAPI;
import com.demonz.velocitynavigator.NavigatorAPIProvider;

NavigatorAPI api = NavigatorAPIProvider.get();
if (api == null) {
    logger.warn("VelocityNavigator is not installed on the network!");
    return;
}
```

---

## Core Interfaces

### `previewRoute(Player player)`
Analyze the routing decision for a player **without physically moving them across the proxy network**. This runs the full pipeline computation cluster (health checks, selection mode, contextual groups, cycles) and returns the asynchronous mathematical result.

```java
api.previewRoute(player).thenAccept(decision -> {
    if (decision.hasSelection()) {
        logger.info("Best lobby for {}: {}", player.getUsername(), decision.selectedServer());
        logger.info("Routing reason/algorithm: {}", decision.reason());
    } else {
        logger.warn("Zero active lobby targets available for {}", player.getUsername());
    }
});
```

### `inspectServer(String serverName)` — v4

Inspect a server's current health status without routing any players. Returns the server's online/offline state, player count, cache information, and health check metadata:

```java
api.inspectServer("lobby-1").thenAccept(status -> {
    logger.info("Server: {} | Online: {} | Players: {} | Cached: {}",
            status.serverName(),
            status.online(),
            status.playersConnected(),
            status.cached());
});
```

This method triggers a fresh health check ping if the cache is stale, and returns a `ServerHealthService.ServerStatus` record.

### `getRoutingDistribution()` — v4

Get per-server connection counts over the last 60 seconds:

```java
Map<String, Long> distribution = api.getRoutingDistribution();
for (Map.Entry<String, Long> entry : distribution.entrySet()) {
    logger.info(entry.getKey() + ": " + entry.getValue() + " connections");
}
```

### `getHealthCheckLatencies()` — v4

> ⚠️ **Placeholder** — This method currently returns an empty map. Latency tracking is planned for a future release.

```java
Map<String, Long> latencies = api.getHealthCheckLatencies();
// Currently returns Map.of() — not yet implemented
```

### `getCircuitBreakerStatuses()` — v4

Get the current circuit breaker state for each server:

```java
Map<String, CircuitBreaker.State> breakers = api.getCircuitBreakerStatuses();
for (Map.Entry<String, CircuitBreaker.State> entry : breakers.entrySet()) {
    logger.info(entry.getKey() + ": " + entry.getValue());  // CLOSED, OPEN, or HALF_OPEN
}
```

---

## Advanced: Party Intercept Pipeline

Route an entire party cache to the *exact same physical backend instance* using VelocityNavigator's context intelligence:

```java
api.previewRoute(partyLeader).thenAccept(decision -> {
    if (decision.hasSelection()) {
        RegisteredServer target = server.getServer(decision.selectedServer()).orElse(null);
        if (target != null) {
            for (Player member : partyMembers) {
                // Execute atomic mass cluster migration
                member.createConnectionRequest(target).fireAndForget();
            }
        }
    }
});
```
