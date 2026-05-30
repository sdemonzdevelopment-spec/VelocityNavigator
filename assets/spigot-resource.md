# VelocityNavigator Spigot Resource Copy

![VelocityNavigator Banner](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/hero-banner.png?v=4)

## Title

VelocityNavigator | Production-Grade Lobby Routing & Initial Join Load Balancing for Velocity

## Short Description

The smartest lobby system for Velocity proxies — true initial join load balancing, health-checked routing, contextual groups, and live debug tools.

## Important Platform Note

> **This is a Velocity-only plugin.** It does not run on Bukkit, Spigot, or Paper backends.
> This Spigot listing exists for visibility and download discovery. Install the JAR on your **Velocity proxy**, not your backend servers.

![Routing](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/feature-routing.png?v=4)

## 🚀 v4.2.0 Feature Highlights

- **Hardened Security & Menu Selections** — Stale or forged lobby menu selections cannot bypass drain mode, capacity checks, or circuit breakers anymore.
- **Prometheus Boot Integration** — Exporter starts immediately during initial proxy boot when enabled.
- **Circuit Breaker Accuracy** — Restored true consecutive-failure tracking behavior for the breaker.
- **Redesigned Configuration (v6)** — The `navigator.toml` file has been completely redesigned with clean section banners and grouped documentation.
- **Unified Notifications** — Aligned admin join notifications with the global `[update_checker].notify_admins` configuration and preserved during config writes.
- **Improved Validation & Normalization** — Support for `latency` routing mode and mixed-case contextual group name matching.

### v4.1.0 Features Included

- **Bedrock/Geyser Form GUI Support:** Seamless routing for Bedrock players with Floodgate UUID mapping. Strips gradients, hovers, and clicks. Bedrock players can use a native, interactive Form GUI (popup menu) to select their lobby, which is highly configurable (custom titles, descriptions, and button formats) and toggled via the `use_gui_for_lobby` setting.
- **`/vn servers` Dashboard:** Paginated lobby diagnostics showing player count, circuit breaker state, drain status, and per-server capacity.
- **Legacy Color Code Converter:** Auto-detects `&` and `§` codes and converts to MiniMessage. Supports `auto`, `legacy`, and `minimessage` modes.
- **Levenshtein Config Validation:** Typo auto-correction with distance-based suggestions for all TOML settings.
- **Self-Documenting Config:** Every key in `navigator.toml` auto-generates rich comments with wiki anchor links.
- **First-Run Welcome & Upgrades:** Console dashboard on fresh install, release notes digest on upgrades.
- **Periodic Update Checker:** Scheduled Modrinth checks with exponential backoff on HTTP 429 (up to 4 hours).
- **Empty Lobby Fallbacks:** Configurable `disconnect` or `fallback_server` strategy when all lobbies are unreachable.
- **Permission Default Change:** `/lobby` now defaults to `"none"` — works out of the box without permission plugins.
- **Initial Join Balancing:** Load-balance players the absolute millisecond they hit the proxy, before they even finish authentication.
- **7 Selection Algorithms:**
  - `least_players` - Best for simple even load distribution.
  - `random` - Uniformly random selection.
  - `round_robin` - Strict sequential cycle.
  - `power_of_two` - Selects the best of 2 random choices (3-5x better tail latency).
  - `weighted_round_robin` - Interleaved WRR with dynamic weights.
  - `least_connections` - Routes using connection tracking (EMA loads).
  - `consistent_hash` - Dynamic consistent hashing on player UUID for deterministic session affinity.
- **Player Affinity (Sticky Sessions):** Send players back to their previously connected lobby with customizable stickiness, configured via the new `[routing.affinity]` block.
- **Contextual Groups:** Route players to game-specific lobbies based on the server they are leaving (e.g. Bedwars → Bedwars lobbies) with per-group selection overrides.
- **Automatic Fallback Loop:** Walk through fallback groups in priority order if a contextual lobby pool is down.

## Network Resilience & High Availability
- **Circuit Breaker Pattern:** Skips unhealthy servers instantly. Features a full `CLOSED` ➔ `OPEN` ➔ `HALF_OPEN` state machine to isolate failing nodes before players notice.
- **Async Health Checks:** Pings backend servers in the background with configurable timeout and TTL caching.
- **Ping Coalescing:** Consolidates overlapping pings (e.g. 100 simultaneous player connections trigger ONE ping per server, preventing network storms).
- **Anti-Spam Cooldowns:** Pre-execution locks block command spam before routing calculations even begin.
- **Connection Retry:** Automatically retries connection attempts on failure, falling back dynamically across candidate servers.

## Live Diagnostics & Maintenance
- `/vn reload` — Hot-reload the entire configuration live.
- `/vn status` — Dashboard of current routing settings, online candidate pools, and health metrics.
- `/vn version` — Displays the installed version and queries for available updates.
- `/vn debug player <name>` — Live walkthrough preview of how the routing engine would resolve a connection for a specific player.
- `/vn debug server <name>` — Detailed health telemetry snapshot of a backend server.
- `/vn drain <server>` — Gracefully empty a lobby server for maintenance. No new connections will be routed to it.
- `/vn undrain <server>` — Resume normal routing to a previously drained server.
- `/vn drain status` — View all currently drained servers.
- `/vn servers` — Show paginated lobby server status dashboard with health, drain, and capacity info.
- `/vn updatecheck` — Manually trigger a query to check for the latest plugin releases.

## Installation

1. Install Velocity 3.x on your proxy
2. Drop the VelocityNavigator JAR into `plugins/`
3. Start the proxy once — a heavily-commented config is auto-generated
4. Edit `plugins/velocitynavigator/navigator.toml`
5. Restart or run `/vn reload`

## Permissions

| Permission | Purpose |
|-----------|---------|
| `velocitynavigator.use` | Use `/lobby` — default changed to `"none"` in v4.1.0 |
| `velocitynavigator.admin` | Admin commands (hidden from non-admins) |
| `velocitynavigator.bypasscooldown` | Bypass `/lobby` cooldown |

## FAQ

**Is this a Spigot/Paper plugin?**
No. VelocityNavigator runs exclusively on **Velocity proxies**. This Spigot listing is for visibility only.

**What Java version do I need?**
Java **17+**.

**What Velocity versions are supported?**
Velocity **3.x**.

**Does it support metrics?**
Yes — bStats plugin id `28341`. Can be disabled in config.

**Will it crash if I write the config wrong?**
No. Every field is validated individually — bad values get a warning log and are replaced with safe defaults. The proxy never crashes from a config typo.

---

## ⚡ Sponsored by Nexeu Hosting

[![nexeu-sponsor](https://whodoesntloveavatars.s3.fra.databucket.eu/assets/promo.png)](https://nexeu.zip/)

Looking for high-performance, reliable, and affordable hosting for your Minecraft server proxy? Check out **[Nexeu Hosting](https://nexeu.zip/)**! Premium hardware, instant setup, and 24/7 support.

👉 **[Get Premium Hosting at nexeu.zip](https://nexeu.zip/)**

---

## 📖 Documentation & Links

- **GitHub Repository**: [View Source](https://github.com/sdemonzdevelopment-spec/VelocityNavigator)
- **Configuration Guide**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Configuration-Guide)
- **Routing Algorithms**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Routing-Algorithms)
- **Initial Join Balancing**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Initial-Join-Balancing)

---

## 📊 Telemetry

[![bStats](https://bstats.org/signatures/velocity/Velocity%20Navigator.svg)](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341)

---

<p align="center">
  <img src="https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/plugin-icon.png?v=4" alt="VelocityNavigator Icon" width="64">
  <br>
  <strong>Built with ❤️ by DemonZ Development</strong>
  <br>
  <em>Premium Minecraft infrastructure, engineered for scale.</em>
</p>
