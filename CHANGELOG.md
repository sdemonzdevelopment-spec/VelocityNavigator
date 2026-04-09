# Changelog

All notable changes to VelocityNavigator are documented in this file.

---

## [3.0.0] — 2026-04-10

### ✨ New Features
- **Initial Join Balancing** — Players are now load-balanced the moment they connect to the proxy, not just when they run `/lobby`. Configurable via `balance_initial_join` in `navigator.toml`.
- **Developer API** — Third-party plugins can now hook into VelocityNavigator via `NavigatorAPI` and `NavigatorAPIProvider`. Preview routes, inspect server health, and read routing config programmatically.
- **Three Routing Modes** — Choose between `least_players`, `round_robin`, and `random` selection algorithms.
- **Contextual Routing** — Route players to game-specific lobbies (e.g., BedWars lobby) based on which server they are leaving.
- **Self-Documenting Config** — The generated `navigator.toml` now includes rich inline comments explaining every setting and routing mode, with links to full documentation.

### 🏥 Reliability
- **Async Health Checks** — Ping candidate lobbies before routing to ensure they are alive. Configurable timeout and caching.
- **Ping Coalescing** — Multiple simultaneous `/lobby` requests share the same `CompletableFuture` ping, preventing network storms on high-traffic proxies.
- **Pre-Execution Cooldown Locking** — The anti-spam cooldown is applied *before* command execution begins, preventing macro-based abuse from bypassing the lock.
- **Graceful Failover** — If all contextual lobbies are offline, the plugin falls back to the default lobby pool instead of showing an error (configurable).

### 📊 Telemetry & Updates
- **bStats Integration** — Anonymous usage telemetry via [bStats](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341) (plugin ID: 28341).
- **Modrinth Update Checker** — Automatic version checking during startup with configurable release channel (`release`, `beta`, `alpha`).

### 🛠 Admin Tools
- `/vn reload` — Hot-reload `navigator.toml` without restarting the proxy.
- `/vn status` — View runtime status (version, routing mode, health checks, bStats, update checker).
- `/vn version` — Check installed vs. latest version.
- `/vn debug player <name>` — Preview the routing decision for a specific player.
- `/vn debug server <name>` — Inspect a server's real-time health snapshot.
- Full tab-completion for all admin commands.

### 📖 Documentation
- Comprehensive `docs/` folder with guides for configuration, routing algorithms, initial join balancing, and the developer API.
- GitHub issue templates (bug report, feature request), PR template, and contribution guidelines.

### 🔧 Configuration
- **Automatic Migration** — Seamless migration from v1/v2 configs with backup generation.
- **Field-Level Validation** — Invalid config values are corrected with warnings, never crashes.
- **MiniMessage Support** — All player-facing messages support MiniMessage rich text formatting.

---

## [2.0.0] — Legacy

Previous version with basic lobby routing. Superseded by v3.0.0.

---

## [1.0.0] — Legacy

Initial release with single-server lobby navigation.

---

*VelocityNavigator is developed and maintained by [DemonZ Development](https://github.com/sdemonzdevelopment-spec).*
