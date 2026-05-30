<p align="center">
  <img src="assets/hero-banner.png?v=4" alt="VelocityNavigator Banner" width="800">
</p>

<h1 align="center">VelocityNavigator</h1>

<p align="center">
  <strong>Premium lobby navigation and intelligent load balancing for Velocity proxies.</strong>
  <br>
  <em>Built by <a href="https://github.com/sdemonzdevelopment-spec">DemonZ Development</a></em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-4.2.0-cyan?style=for-the-badge" alt="Version">
  <img src="https://img.shields.io/badge/platform-Velocity_3.x-blue?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/java-17+-orange?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/license-Apache_2.0-green?style=for-the-badge" alt="License">
</p>

---

## 🆕 What's New in v4.2

| Feature | Description |
|---------|-------------|
| 🧱 **Bedrock/Geyser Support** | Seamless routing for Bedrock players with Floodgate UUID mapping and format stripping |
| 🖥️ **`/vn servers` Dashboard** | Paginated diagnostics dashboard with circuit breaker, drain, and player capacity per lobby |
| 🎨 **Configurable Dashboard Colors** | Custom MiniMessage/RGB status colors for healthy, draining, open, and offline states |
| 🔍 **Legacy Color Code Converter** | Auto-detects and converts `&`/`§` codes to MiniMessage with `auto`, `legacy`, or `minimessage` modes |
| ✅ **Levenshtein Config Validation** | Typo auto-correction with distance-based suggestions for all enum-styled TOML keys |
| 📖 **Self-Documenting Config** | Every TOML key gets rich comments + wiki anchor URLs on write/migration |
| 👋 **First-Run Welcome & Upgrades Digest** | Console welcome dashboard on fresh install, release notes digest on upgrades |
| 🔄 **Periodic Update Checker** | Scheduled update checks with exponential 429 backoff (scales up to 4 hours) |
| 🚫 **Empty Lobby Fallbacks** | Configurable `disconnect` or `fallback_server` strategy when all lobbies are unreachable |
| 🔓 **Permission Default Changed** | `/lobby` command now defaults to `"none"` for immediate out-of-the-box adoption |

### v4.0 Features Included

| Feature | Description |
|---------|-------------|
| 🧠 **4 New Selection Algorithms** | `power_of_two`, `weighted_round_robin`, `least_connections`, `consistent_hash` — 7 total |
| 🛡️ **Circuit Breaker** | Automatic server failure detection with CLOSED → OPEN → HALF_OPEN state machine |
| 💾 **Player Affinity** | Sticky sessions with configurable stickiness probability |
| 🔧 **Server Drain Mode** | Gracefully take servers offline for maintenance with `/vn drain` |
| 🔄 **Connection Retry with Fallback** | Automatically retry on connection failure with configurable attempts |
| 📊 **Routing Metrics API** | Monitor distribution, health check latencies, and circuit breaker states |
| ⚖️ **Per-Group Selection Mode** | Contextual groups can override the global selection algorithm |
| 🔗 **Fallback Priority Chain** | Ordered fallback groups when a group's servers are unavailable |
| 📉 **Graceful Degradation** | Fall back to random selection when all health checks fail |
| 🌍 **Geo-Based Routing** | Experimental geo-routing with MaxMind GeoLite2 support |
| 🔔 **Admin Update Notifications** | Automatic in-game notification for admins when updates are available |

→ See [Migration Guide](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Migration-Guide-v3-to-v4) for upgrade instructions.

---

## ✨ Feature Highlights

| Feature | Description |
|---------|-------------|
| 🧠 **7 Routing Algorithms** | `least_players` \| `round_robin` \| `random` \| `power_of_two` \| `weighted_round_robin` \| `least_connections` \| `consistent_hash` |
| ⚡ **Initial Join Balancing** | Players are load-balanced the moment they connect, not just when they type `/lobby` |
| 🔀 **Contextual Routing** | Route players to game-specific lobbies based on which server they're leaving |
| 🛡️ **Circuit Breaker** | Automatic failure detection — unhealthy servers are skipped until they recover |
| 🏥 **Async Health Checks** | Ping candidate lobbies before routing with configurable timeout + caching |
| 🛡️ **Ping Coalescing** | Multiple simultaneous requests share one ping — no network storms |
| 💾 **Player Affinity** | Sticky sessions so players tend to return to the same lobby |
| 🔧 **Server Drain Mode** | `/vn drain` and `/vn undrain` for maintenance |
| 📊 **bStats Telemetry** | Anonymous usage metrics via [bStats](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341) |
| 🔌 **Developer API** | Third-party plugins can hook into routing via `NavigatorAPI` |
| 📖 **Self-Documenting Config** | `navigator.toml` generates with inline docs explaining every setting |
| 🛠️ **Full Admin Suite** | `/vn status`, `/vn reload`, `/vn debug`, `/vn drain`, `/vn updatecheck` with tab-completion |

---

## 📦 Installation

1. Download `VelocityNavigator-4.2.0.jar` from [Releases](../../releases)
2. Place it in your Velocity proxy's `plugins/` folder
3. Start (or restart) the proxy
4. Edit `plugins/velocitynavigator/navigator.toml` to configure

**Requirements**: Velocity 3.x • Java 17+

---

## ⚙️ Quick Configuration

```toml
[routing]
# 7 modes: least_players, round_robin, random, power_of_two,
#          weighted_round_robin, least_connections, consistent_hash
selection_mode = "power_of_two"

# Balance players when they first connect (not just /lobby)
balance_initial_join = true

# Your lobby servers (plain strings or inline tables)
default_lobbies = [
  { server = "lobby-1", max_players = 100, weight = 2 },
  { server = "lobby-2", max_players = 100, weight = 2 },
  "lobby-3",
]

# Circuit breaker: skip unhealthy servers automatically
[routing.circuit_breaker]
enabled = true
failure_threshold = 3
cooldown_seconds = 30
```

See the [Configuration Guide](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Configuration-Guide) for all settings.

---

## 🛠️ Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/lobby` | `velocitynavigator.use` | Send to the best available lobby |
| `/hub`, `/spawn` | `velocitynavigator.use` | Aliases for `/lobby` |
| `/vn reload` | `velocitynavigator.admin` | Hot-reload navigator.toml |
| `/vn status` | `velocitynavigator.admin` | View runtime status, distribution, circuit breakers |
| `/vn debug player <name>` | `velocitynavigator.admin` | Preview routing decision |
| `/vn debug server <name>` | `velocitynavigator.admin` | Inspect server health and circuit breaker |
| `/vn drain <server>` | `velocitynavigator.admin` | Drain a server (no new players) |
| `/vn undrain <server>` | `velocitynavigator.admin` | Remove drain flag |
| `/vn drain status` | `velocitynavigator.admin` | List drained servers |
| `/vn servers` | `velocitynavigator.admin` | Show paginated lobby server status dashboard |
| `/vn updatecheck` | `velocitynavigator.admin` | Manually check for updates |

---

## 🔑 Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `velocitynavigator.use` | `none*` | Use the lobby command — default changed to `"none"` in v4.1.0 |
| `velocitynavigator.admin` | `false` | Use all `/vn` admin commands |
| `velocitynavigator.bypass.cooldown` | `false` | Bypass command cooldown |
| `velocitynavigator.bypasscooldown` | `false` | Legacy — still works, use `bypass.cooldown` instead |

---

## 🔌 Developer API

Other Velocity plugins can integrate with VelocityNavigator:

```java
NavigatorAPI api = NavigatorAPIProvider.get();
if (api != null) {
    // Preview a routing decision
    api.previewRoute(player).thenAccept(decision -> {
        System.out.println("Best lobby: " + decision.selectedServer());
    });

    // Get routing metrics (v4)
    Map<String, Integer> distribution = api.getRoutingDistribution();
    Map<String, Long> latencies = api.getHealthCheckLatencies();
    Map<String, String> breakers = api.getCircuitBreakerStatuses();
}
```

See the [Developer API Guide](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Developer-API) for full documentation.

---

## 📖 Documentation

| Document | Description |
|----------|-------------|
| [Quick Start Guide](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Quick-Start-Guide) | Get running in under 10 minutes |
| [Configuration Guide](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Configuration-Guide) | Every `navigator.toml` setting explained |
| [Routing Algorithms](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Routing-Algorithms) | Deep dive into all 7 routing modes |
| [Algorithm Visualizations](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Algorithm-Visualizations) | Distribution patterns at different load levels |
| [Contextual Routing Guide](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Contextual-Routing-Guide) | Per-game-mode lobby routing |
| [GeoIP Setup](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/GeoIP-Database-Setup) | Database setup (GeoLite2) and FAQ |
| [Operations Runbook](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Operations-Runbook) | Drain, circuit breaker, troubleshooting |
| [Migration v3 → v4](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Migration-Guide-v3-to-v4) | Step-by-step upgrade guide |
| [Troubleshooting](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Troubleshooting-Guide) | Symptom-based debugging |
| [FAQ](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/FAQ) | Common questions answered |
| [Developer API](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Developer-API) | Integrate from your own plugins |
| [Changelog](CHANGELOG.md) | Full release history |
| [Contributing](CONTRIBUTING.md) | How to contribute |

---

## 🏗️ Building from Source

```bash
git clone https://github.com/sdemonzdevelopment-spec/VelocityNavigator.git
cd VelocityNavigator
mvn clean verify
# JAR output: target/VelocityNavigator-4.2.0.jar
```

---

## 📊 Stats

[![bStats](https://bstats.org/signatures/velocity/Velocity%20Navigator.svg)](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341)

---

<p align="center">
  <img src="assets/plugin-icon.png?v=4" alt="VelocityNavigator Icon" width="64">
  <br>
  <strong>Built with ❤️ by <a href="https://github.com/sdemonzdevelopment-spec">DemonZ Development</a></strong>
  <br>
  <em>Premium Minecraft infrastructure, engineered for scale.</em>
</p>
