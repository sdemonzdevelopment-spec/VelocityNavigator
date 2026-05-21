# VelocityNavigator Modrinth Listing

![VelocityNavigator Banner](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/hero-banner.png?v=4)

**VelocityNavigator v4** is a production-grade Velocity proxy plugin that introduces absolute traffic control over your network through intelligent load balancing, circuit breaker resilience, and a highly context-aware `/lobby` system.

No more funneling all new players into a single hub. No more sending players to offline servers. No more guessing which lobby they ended up on.

![Routing](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/feature-routing.png?v=4)

## What's New in v4

- **7 Selection Algorithms** — `least_players`, `random`, `round_robin`, `power_of_two` (recommended), `weighted_round_robin`, `least_connections`, `consistent_hash`
- **Circuit Breaker** — Automatic failure detection with CLOSED → OPEN → HALF_OPEN state machine
- **Player Affinity** — Sticky sessions so players return to their previous lobby
- **Server Drain Mode** — Gracefully empty servers for maintenance with `/vn drain`
- **Connection Retry & Fallback Chain** — Configurable retry with priority-based fallback
- **Admin Update Notifications** — Automatic check on proxy start + admin join alerts
- **Per-Group Mode Override** — Different routing algorithms per contextual group
- **Self-Documenting Config** — Auto-migrates from v3 with `.bak` backup

## Why VelocityNavigator?

### For Players
- `/lobby` just works — fast, reliable, with clear feedback messages.
- Always routes to a healthy, reachable server.
- Automatically places them into the best lobby immediately upon joining the network before they even finish authentication.
- Sticky sessions mean players rejoin the lobby they were last on.

### For Admins
- **Initial Join Balancing** — Bypasses Velocity's default static fallback engine to perfectly split initial proxy connection waves.
- **7 routing algorithms** — From simple `random` to advanced `power_of_two` with 3-5x better tail latency.
- **Circuit Breaker** — Automatically stops routing to failing servers before players notice.
- **Server Drain Mode** — Safely empty lobbies for maintenance without kicking players.
- **Contextual lobby groups** — Different game servers can point to different lobby pools with automatic fallback loops.
- **Real-time health checks** — Async pings with configurable timeout, TTL caching, and ping coalescing to prevent backend storms.
- **Connection retry** — Automatically retries on connection failure with configurable fallback chain.
- **Anti-spam protection** — Pre-execution cooldown locks block macro abuse before the routing engine even fires.
- **Update notifications** — Checks for updates on startup and notifies admins when they join.

## How Routing Works

```
Player runs /lobby
    ↓
Player affinity check (return to previous lobby if sticky)
    ↓
Contextual group resolution (if enabled)
    ↓
Fallback to default lobbies (if contextual group is empty/offline)
    ↓
Cycle pruning (remove current server if alternatives exist)
    ↓
Drain filter (skip servers in drain mode)
    ↓
Circuit breaker check (skip OPEN/circuit-tripped servers)
    ↓
Async health checks (with TTL cache + ping coalescing)
    ↓
Selection strategy (7 algorithms available)
    ↓
Connection attempt with retry + fallback chain
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
| `/vn debug player <name>` | `velocitynavigator.admin` | Preview routing for a player |
| `/vn debug server <name>` | `velocitynavigator.admin` | Inspect server health snapshot |
| `/vn drain <server>` | `velocitynavigator.admin` | Drain a server for maintenance |
| `/vn undrain <server>` | `velocitynavigator.admin` | Remove drain mode from a server |
| `/vn drain status` | `velocitynavigator.admin` | View all drained servers |
| `/vn updatecheck` | `velocitynavigator.admin` | Check for plugin updates |

## Quick Config Example

```toml
# VelocityNavigator v4.0.0 Configuration

notify_on_startup = true
notify_admins_on_join = true

[commands]
primary = "lobby"
aliases = ["hub", "spawn"]
permission = "velocitynavigator.use"
admin_aliases = ["velocitynavigator", "vn"]
cooldown_seconds = 3
reconnect_if_same_server = false

[routing]
selection_mode = "power_of_two"
cycle_when_possible = true
balance_initial_join = true
max_retries = 2

default_lobbies = [
  { server = "lobby-1", max_players = 100, weight = 3 },
  { server = "lobby-2", max_players = 100, weight = 2 },
  { server = "lobby-3", max_players = 50, weight = 1 },
]

[routing.affinity]
enabled = true
stickiness = 0.7

[circuit_breaker]
enabled = true
failure_threshold = 3
cooldown_seconds = 30
half_open_max_tests = 1

[health_checks]
enabled = true
timeout_ms = 2500
cache_seconds = 60

[update_checker]
channel = "release"
```

## Compatibility

- **Platform:** Velocity only
- **Velocity:** `3.x`
- **Java:** `17+`

---

## ⚡ Sponsored by Nex EU Hosting

Looking for high-performance, reliable, and affordable hosting for your Minecraft server proxy? Check out **[Nex EU Hosting](https://nexeu.zip/)**! Premium hardware, instant setup, and 24/7 support.

👉 **[Get Premium Hosting at nexeu.zip](https://nexeu.zip/)**

---

## Documentation & Links

- **GitHub Repository**: [View Source](https://github.com/sdemonzdevelopment-spec/VelocityNavigator)
- **Configuration Guide**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Configuration-Guide)
- **Routing Algorithms**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Routing-Algorithms)
- **Circuit Breaker**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Circuit-Breaker)
- **Initial Join Balancing**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Initial-Join-Balancing)
- **Operations Runbook**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Operations-Runbook)
- **Migration Guide (v3 to v4)**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Migration-Guide)

---

## Telemetry

[![bStats](https://bstats.org/signatures/velocity/Velocity%20Navigator.svg)](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341)

---

<p align="center">
  <img src="https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/plugin-icon.png?v=4" alt="VelocityNavigator Icon" width="64">
  <br>
  <strong>Built with care by DemonZ Development</strong>
  <br>
  <em>Premium Minecraft infrastructure, engineered for scale.</em>
</p>
