# Changelog

All notable changes to VelocityNavigator are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [4.2.0] - 2026-05-30

### Fixed

- Hardened Java and Bedrock lobby menu selections so stale or manually forged choices cannot bypass drain mode, circuit breakers, capacity checks, or configured lobby pools.
- Started the Prometheus exporter during initial proxy boot when enabled, not only after `/vn reload`.
- Restored true consecutive-failure behavior for the circuit breaker.
- Preserved update notification settings during config rewrites and aligned admin join notifications with `[update_checker].notify_admins`.
- Accepted the documented `latency` routing mode in config validation and migration normalization.
- Normalized contextual group names consistently so mixed-case mappings continue to route.
- Aligned Maven artifact version, Velocity plugin metadata, and user-facing docs for the 4.2.0 release.

### Changed

- **Config version bumped to 6.** The generated `navigator.toml` has been completely redesigned with section banners, grouped documentation, and a friendlier layout. Existing configs are auto-migrated and backed up.

---

## [4.1.0] - 2026-05-26

### Added

- **Bedrock/Geyser Player Support** — Soft-dependency integration with Geyser and Floodgate. Strips advanced Kyori Component formatting (gradients, hover, click actions) to display beautifully on Bedrock clients, and maps authentic Java UUIDs for player affinity tracking.
- **First-Run Experience Polish** — Beautiful console-printed welcome dashboard on fresh installs. Detects plugin upgrades and showcases a clean release notes changelog digest.
- **`/vn servers` Diagnostics Command** — Elegant paginated status dashboard for all configured lobbies, complete with player count/max capacity limits, circuit breaker state tracking, and drain statuses.
- **Configurable Dashboard Colors** — Customizable status tags/colors for `/vn servers` supporting full hex, RGB, and MiniMessage styling in `navigator.toml`.
- **Typo Auto-Correction & Levenshtein Validation** — Dynamic typo detection on config reload/load using Levenshtein distance metrics (e.g., suggesting `"least_players"` for `"leadt_players"`).
- **Self-Documenting Configuration Keys** — The entire `navigator.toml` file comments are dynamically populated on generation/migration, pointing users to the exact section anchor of the official Wiki page.
- **Automatic Legacy Color Code Converter** — Seamlessly matches and converts all 22 standard `&` and `§` legacy formatting codes to MiniMessage syntax on load (supports `"auto"` with one-time warnings, `"minimessage"`, or `"legacy"` modes).
- **Periodic Update Checker with Backoff** — Recurring scheduled task loop to check for updates with exponential backoff on HTTP 429 errors (scales dynamically up to 4 hours).
- **Empty Lobby Routing Fallbacks** — Customizable degradation strategies (`"disconnect"` or `"fallback_server"`) when all primary lobby options are offline or circuit-broken.
- **Permission Default Change** — Standardized `/lobby` default command permission changed to `"none"` for immediate out-of-the-box adoption. Existing migration preserves existing admin-defined configurations safely.

## [4.0.0] - 2026-05-01

### Added

- **Power of Two selection algorithm** (`power_of_two`) — picks two random candidates, selects the one with fewer players. Near-optimal distribution at O(1) cost.
- **Weighted Round Robin selection algorithm** (`weighted_round_robin`) — interleaved WRR that distributes traffic proportionally to server weights.
- **Least Connections selection algorithm** (`least_connections`) — selects the server with the lowest exponential moving average (EMA) of connection load and rate.
- **Consistent Hash selection algorithm** (`consistent_hash`) — deterministic player-to-server mapping using a consistent hash ring with 150 virtual nodes and SHA-256 hashing. Provides session affinity.
- **LobbyEntry format** — servers can be configured as plain strings or inline tables with `max_players` and `weight` fields. Backward compatible with plain strings.
- **Per-lobby max-player cap** — servers at their `max_players` capacity are automatically excluded from routing.
- **Circuit Breaker** — automatic server failure detection with CLOSED → OPEN → HALF_OPEN state machine. Unhealthy servers are excluded from routing until they recover.
- **Server Drain Mode** — `/vn drain <server>`, `/vn undrain <server>`, `/vn drain status` commands for graceful server maintenance.
- **Connection Retry with Fallback** — automatic retry on connection failure with configurable `max_retries`. Shows retry message with `<attempt>/<max>` placeholders.
- **Per-Group Selection Mode Override** — contextual routing groups can specify their own `mode`, overriding the global `selection_mode`.
- **Fallback Priority Chain** — ordered fallback groups when a contextual group's servers are all unavailable.
- **Player Affinity Routing** — sticky sessions with configurable `stickiness` probability (0.0–1.0). Players tend to return to their previous lobby.
- **Graceful Degradation** — when all health checks fail, fall back to a configured degradation mode (default: `random`) instead of showing "No lobby found".
- **Geo-Based Routing (experimental)** — stub implementation for geo-based lobby routing using MaxMind GeoLite2 Country database.
- **Routing Metrics API** — new `NavigatorAPI` methods: `getRoutingDistribution()`, `getHealthCheckLatencies()`, `getCircuitBreakerStatuses()`.
- **Connection Rate Tracking** — sliding window (60-second) connection rate tracker used by `least_connections` mode.
- **Server Load Tracking** — EMA-based server load tracker used by `least_connections` mode.
- **Routing Stats** — per-server connection counts with 60-second reset, shown in `/vn status`.
- **Enhanced /vn status dashboard** — now shows circuit breaker status, drained servers, and routing distribution.
- **`/vn updatecheck` command** — manually check for updates (replaces recurring auto-update check).
- **Startup update notification** — one-time update check 5 seconds after proxy start.
- **Admin join update notification** — players with `velocitynavigator.admin` permission are notified in-game when they join if an update is available. Controlled by `notify_admins_on_join` config.
- **`<player>` placeholder** — new placeholder available in all message templates.
- **`<attempt>` and `<max>` placeholders** — available in `messages.retrying`.
- **`messages.retrying` config** — new message template for connection retry notifications.
- **`notify_on_startup` config** — suppress startup update notification.
- **`notify_admins_on_join` config** — enable/disable in-game admin update notification on join.
- **Health check cache purge** — expired cache entries are automatically purged every 60 seconds.
- **`getCachedOnlineServers()` method** — synchronous cached player count access for initial join balancing (replaces blocking `.join()` call).
- **Config version field** — `CURRENT_VERSION` set to 4. Auto-migration from v3 configs with `.bak` backup.

### Changed

- **Removed `.join()` blocking call** in `onPlayerChooseInitialServer` — replaced with synchronous cache lookup. Falls through to Velocity's built-in try list on cold start.
- **Round-robin state only resets when lobby topology changes** — `applyLoadedConfiguration()` now compares previous and current lobby lists before resetting.
- **Contextual groups** — changed from `Map<String, List<LobbyEntry>>` to `Map<String, GroupConfig>` where GroupConfig contains `servers` and optional `mode`.
- **UpdateChecker** — removed recurring schedule; now runs a single check on startup. Removed `enabled`, `notifyConsole`, `startupDelaySeconds` fields.
- **ConfigManager** — reads both plain strings and inline tables for lobby entries (backward compatible). Writes inline tables when `max_players` or `weight` is non-default.
- **MessageFormatter** — added `player`, `attempt`, `max` to allowed placeholders.
- **`noLobbyFound` message** — now includes `(<reason>)` placeholder by default.
- **`ServerCandidate` record** — now includes `effectiveWeight` and `emaLoad` fields.
- **`RouteDecision`** — provides ordered candidate list for retry fallback.

### Fixed

- **Blocking `.join()` in event handler** — `onPlayerChooseInitialServer` no longer blocks the event loop with `.join()` calls. Uses cached data synchronously instead.
- **Round-robin reset on every reload** — round-robin counter is now only reset when the lobby topology actually changes, preventing unnecessary redistribution on config reload.
- **Health check cache memory leak** — expired cache entries are now purged every 60 seconds.
- **Permission node inconsistency** — `velocitynavigator.bypasscooldown` now also checks `velocitynavigator.bypass.cooldown` for consistency.

### Deprecated

- **`velocitynavigator.bypasscooldown` permission** — use `velocitynavigator.bypass.cooldown` instead. The legacy name still works as a fallback.

### Removed

- **`update_checker.enabled` config field** — the update checker now always runs on startup.
- **`update_checker.notifyConsole` config field** — update notifications are always logged to console.
- **`update_checker.startupDelaySeconds` config field** — startup delay is fixed at 5 seconds.
- **Recurring update check schedule** — replaced by one-time startup check and `/vn updatecheck`.

---

## [3.0.0] — 2026-04-10

### Added

- **Initial Join Balancing** — Players are load-balanced the moment they connect to the proxy via `PlayerChooseInitialServerEvent`.
- **Developer API** — `NavigatorAPI` and `NavigatorAPIProvider` for third-party plugin integration.
- **Three Routing Modes** — `least_players`, `round_robin`, and `random` selection algorithms.
- **Contextual Routing** — Route players to game-specific lobbies based on which server they are leaving.
- **Self-Documenting Config** — `navigator.toml` generates with inline comments explaining every setting.

### Changed

- **Async Health Checks** — Ping candidate lobbies before routing with configurable timeout and caching.
- **Ping Coalescing** — Multiple simultaneous `/lobby` requests share the same `CompletableFuture` ping.
- **Pre-Execution Cooldown Locking** — Cooldown is applied before command execution to prevent macro abuse.
- **Graceful Failover** — Falls back to default lobby pool when all contextual lobbies are offline.

### Added (Telemetry & Updates)

- **bStats Integration** — Anonymous usage telemetry (plugin ID: 28341).
- **Modrinth Update Checker** — Automatic version checking with configurable release channel.

### Added (Admin Tools)

- `/vn reload` — Hot-reload `navigator.toml`.
- `/vn status` — View runtime status.
- `/vn version` — Check installed vs. latest version.
- `/vn debug player <name>` — Preview routing decision.
- `/vn debug server <name>` — Inspect server health.
- Full tab-completion for all admin commands.

### Added (Configuration)

- **Automatic Migration** — Seamless migration from v1/v2 configs with backup generation.
- **Field-Level Validation** — Invalid config values are corrected with warnings.
- **MiniMessage Support** — All player-facing messages support MiniMessage rich text formatting.

---

## [2.0.0] — Legacy

Previous version with basic lobby routing. Superseded by v3.0.0.

---

## [1.0.0] — Legacy

Initial release with single-server lobby navigation.

---

*VelocityNavigator is developed and maintained by [DemonZ Development](https://github.com/sdemonzdevelopment-spec).*
