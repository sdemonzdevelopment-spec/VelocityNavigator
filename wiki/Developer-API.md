# Developer API Hooking

> Integrate deeply with the VelocityNavigator backbone from your own private Velocity proxy plugins and game modes!

---

## Dependency Injector

Add VelocityNavigator as a Maven compilation dependency in your core `pom.xml`:

```xml
<dependency>
    <groupId>com.demonz</groupId>
    <artifactId>VelocityNavigator</artifactId>
    <version>3.0.0</version>
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

### Advanced: Party Intercept Pipeline

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
