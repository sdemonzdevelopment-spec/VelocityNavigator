# VelocityNavigator Modrinth Listing

![VelocityNavigator Banner](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/hero-banner.png)

**VelocityNavigator** is a production-grade Velocity proxy plugin that introduces absolute traffic control over your network through intelligent initial-join load balancing and a highly context-aware `/lobby` system.

No more funneling all new players into a single hub. No more sending players to offline servers. No more guessing which lobby they ended up on.

![Routing](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/feature-routing.png)

## Why VelocityNavigator?

### For Players
- `/lobby` just works — fast, reliable, with clear feedback messages.
- Always routes to a healthy, reachable server.
- Automatically places them into the best lobby immediately upon joining the network before they even finish authentication.

### For Admins
- **Initial Join Balancing** — Bypasses Velocity's default static fallback engine to perfectly split initial proxy connection waves.
- **Three routing algorithms** — `least_players`, `random`, `round_robin`
- **Contextual lobby groups** — different game servers can point to different lobby pools with automatic fallback loops
- **Real-time health checks** — async pings with configurable timeout, TTL caching, and ping coalescing to prevent backend storms
- **Anti-spam protection** — pre-execution cooldown locks block macro abuse before the routing engine even fires

## How Routing Works

```
Player runs /lobby
    ↓
Contextual group resolution (if enabled)
    ↓
Fallback to default lobbies (if contextual group is empty/offline)
    ↓
Cycle pruning (remove current server if alternatives exist)
    ↓
Async health checks (with TTL cache + ping coalescing)
    ↓
Selection strategy (least_players / random / round_robin)
    ↓
Player is connected to the best available lobby
```

*All server name matching is **case-insensitive** — `Lobby-1` and `lobby-1` are treated identically.*

## Commands & Diagnostics

| Command | Permission | Description |
|---------|-----------|-------------|
| `/lobby` | `velocitynavigator.use` | Send to best available lobby |
| `/vn reload` | `velocitynavigator.admin` | Reload config live |
| `/vn status` | `velocitynavigator.admin` | Runtime status dashboard |
| `/vn version` | `velocitynavigator.admin` | Installed + latest version info |
| `/vn debug player <name>` | `velocitynavigator.admin` | Preview routing for a player |
| `/vn debug server <name>` | `velocitynavigator.admin` | Inspect server health snapshot |

## Quick Config Example

```toml
config_version = 3

[commands]
primary = "lobby"
aliases = ["hub", "spawn"]

[routing]
selection_mode = "least_players"
balance_initial_join = true
cycle_when_possible = true
default_lobbies = ["lobby-1", "lobby-2"]

[routing.contextual]
enabled = true
fallback_to_default = true

[routing.contextual.groups]
bedwars = ["bw-lobby-1", "bw-lobby-2"]

[routing.contextual.sources]
"bedwars-1" = "bedwars"

[health_checks]
enabled = true
timeout_ms = 2500
cache_seconds = 60
```

## Compatibility

- **Platform:** Velocity only
- **Velocity:** `3.x`
- **Java:** `17+`

---

## 📖 Documentation & Links

- **GitHub Repository**: [View Source](https://github.com/sdemonzdevelopment-spec/VelocityNavigator)
- **Configuration Guide**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/blob/main/docs/configuration-guide.md)
- **Routing Algorithms**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/blob/main/docs/routing-algorithms.md)
- **Initial Join Balancing**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/blob/main/docs/initial-join-balancing.md)

---

## 📊 Telemetry

[![bStats](https://bstats.org/signatures/velocity/Velocity%20Navigator.svg)](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341)

---

<p align="center">
  <img src="https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/plugin-icon.png" alt="VelocityNavigator Icon" width="64">
  <br>
  <strong>Built with ❤️ by DemonZ Development</strong>
  <br>
  <em>Premium Minecraft infrastructure, engineered for scale.</em>
</p>
