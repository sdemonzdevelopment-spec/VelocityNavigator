# VelocityNavigator Developer API

> Integrate with VelocityNavigator from your own Velocity plugins.

---

## Maven Dependency

Add VelocityNavigator as a dependency in your `pom.xml`:

```xml
<dependency>
    <groupId>com.demonz</groupId>
    <artifactId>VelocityNavigator</artifactId>
    <version>3.0.0</version>
    <scope>provided</scope>
</dependency>
```

> **Note**: Use `<scope>provided</scope>` since VelocityNavigator will already be loaded on the proxy at runtime.

Make sure your plugin depends on VelocityNavigator in your `@Plugin` annotation:

```java
@Plugin(
    id = "your-plugin",
    dependencies = { @Dependency(id = "velocitynavigator") }
)
```

---

## Accessing the API

```java
import com.demonz.velocitynavigator.NavigatorAPI;
import com.demonz.velocitynavigator.NavigatorAPIProvider;

// Get the API instance (null if VelocityNavigator isn't loaded)
NavigatorAPI api = NavigatorAPIProvider.get();
if (api == null) {
    logger.warn("VelocityNavigator is not installed!");
    return;
}
```

---

## API Methods

### `previewRoute(Player player)`
Preview the routing decision for a player **without moving them**. This runs the full pipeline (health checks, selection mode, contextual groups) and returns the result.

```java
api.previewRoute(player).thenAccept(decision -> {
    if (decision.hasSelection()) {
        logger.info("Best lobby for {}: {}", player.getUsername(), decision.selectedServer());
        logger.info("Routing reason: {}", decision.reason());
    } else {
        logger.warn("No lobby available for {}", player.getUsername());
    }
});
```

### `inspectServer(String serverName)`
Get real-time health data for a specific server.

```java
api.inspectServer("lobby-1").thenAccept(status -> {
    logger.info("Server: {} | Online: {} | Players: {}",
        status.serverName(), status.online(), status.playersConnected());
});
```

### `getRoutingConfig()`
Access the current routing configuration.

```java
Config.Routing routing = api.getRoutingConfig();
logger.info("Mode: {}", routing.selectionMode().configValue());
logger.info("Default lobbies: {}", routing.defaultLobbies());
logger.info("Balance initial join: {}", routing.balanceInitialJoin());
```

### `getSelectionMode()`
Quick access to the active selection mode.

```java
Config.SelectionMode mode = api.getSelectionMode();
switch (mode) {
    case LEAST_PLAYERS -> logger.info("Using intelligent load balancing");
    case ROUND_ROBIN -> logger.info("Using sequential rotation");
    case RANDOM -> logger.info("Using random selection");
}
```

---

## Example: Custom Party Routing

Route an entire party to the same lobby using VelocityNavigator's routing engine:

```java
api.previewRoute(partyLeader).thenAccept(decision -> {
    if (decision.hasSelection()) {
        RegisteredServer target = server.getServer(decision.selectedServer()).orElse(null);
        if (target != null) {
            for (Player member : partyMembers) {
                member.createConnectionRequest(target).fireAndForget();
            }
        }
    }
});
```

---

## API Availability

The API is available after VelocityNavigator's `ProxyInitializeEvent` handler completes. It is cleared during `ProxyShutdownEvent`. Always null-check with `NavigatorAPIProvider.get()` before use.
