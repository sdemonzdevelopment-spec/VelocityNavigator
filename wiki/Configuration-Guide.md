# VelocityNavigator Configuration Guide

> Complete reference for every setting in `navigator.toml` — v4.2.0

---

## Overview

The configuration file `navigator.toml` is organized into these sections:

1. `[commands]` — Player commands, aliases, permissions, cooldown
2. `[messages]` — All player-facing messages with MiniMessage formatting, legacy color conversion, and dashboard status colors
3. `[routing]` — Selection algorithm, lobby pool, and core routing behavior
4. `[circuit_breaker]` — Automatic failure detection
5. `[degradation]` — Fallback behavior when all health checks fail
6. `[routing.affinity]` — Player Affinity (Sticky Sessions) configuration
7. `[geo_routing]` — Geo-based routing (experimental)
8. `[routing.contextual]` — Context-aware routing groups
9. `[health_checks]` — Server monitoring configuration
10. `[update_checker]` — Update check settings
11. `[startup]` — First-run welcome and upgrades digest
12. `[bedrock]` — Bedrock/Geyser player support
13. `[lobby]` — Empty lobby fallback strategy
14. `[metrics]` — bStats integration
15. `[debug]` — Verbose logging

Top-level: `notify_on_startup`, `notify_admins_on_join`

> **New in v4.1**: Legacy color conversion (`messages.formatting`), dashboard status colors (`messages.dashboard_*`), first-run welcome (`[startup]`), Bedrock/Geyser (`[bedrock]`), empty lobby strategy (`[lobby]`), and Levenshtein config validation.

---

## `[commands]`

Controls what players type and what permissions are required.

```toml
[commands]
primary = "lobby"
aliases = ["hub", "spawn"]
permission = "velocitynavigator.use"
admin_aliases = ["velocitynavigator", "vn"]
cooldown_seconds = 3
reconnect_if_same_server = false
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `primary` | string | `"lobby"` | The main command players type (e.g. `/lobby`). |
| `aliases` | string[] | `["hub", "spawn"]` | Alternative commands that map to the primary. |
| `permission` | string | `"none"` | Permission required to use the lobby command. Set to `"none"` to allow all players. **Default changed from `"velocitynavigator.use"` to `"none"` in v4.1.0.** |
| `admin_aliases` | string[] | `["velocitynavigator", "vn"]` | Aliases for the admin command. |
| `cooldown_seconds` | int | `3` | Anti-spam cooldown in seconds between lobby commands. |
| `reconnect_if_same_server` | boolean | `false` | Whether to reconnect a player even if they're already on the selected lobby. |

---

## `[messages]`

All player-facing messages support [MiniMessage](https://docs.advntr.dev/minimessage/format.html) rich text formatting and placeholders.

```toml
[messages]
connecting = "<aqua>Sending you to <server>...</aqua>"
alreadyConnected = "<yellow>You are already connected to <server>.</yellow>"
noLobbyFound = "<red>No available lobby could be found. (<reason>)</red>"
playerOnly = "<gray>This command can only be used by a player.</gray>"
cooldown = "<yellow>Please wait <time> more second(s).</yellow>"
reloadSuccess = "<green>VelocityNavigator reloaded.</green>"
reloadFailed = "<red>Reload failed. Check console for details.</red>"
retrying = "<yellow>Retrying connection... (<attempt>/<max>)</yellow>"
formatting = "auto"
dashboard_healthy = "<green>"
dashboard_draining = "<yellow>"
dashboard_open = "<red>"
dashboard_offline = "<gray>"
```

| Setting | Type | Default | Placeholders | Description |
|---------|------|---------|-------------|-------------|
| `noLobbyFound` | string | `"<red>No available lobby could be found. (<reason>)</red>"` | `<reason>`, `<mode>`, `<player>` | Shown when no lobby is available. |
| `cooldown` | string | `"<yellow>Please wait <time> more second(s).</yellow>"` | `<time>`, `<player>` | Shown when cooldown is active. |
| `alreadyConnected` | string | `"<yellow>You are already connected to <server>.</yellow>"` | `<server>`, `<player>` | Shown when player is already on the selected lobby. |
| `connecting` | string | `"<aqua>Sending you to <server>...</aqua>"` | `<player>`, `<server>` | Shown while connecting. |
| `retrying` | string | `"<yellow>Retrying connection... (<attempt>/<max>)</yellow>"` | `<attempt>`, `<max>`, `<player>`, `<server>` | Shown on each retry attempt. **New in v4.** |
| `formatting` | string | `"auto"` | — | Color format mode: `"auto"` (detect + one-time warning), `"minimessage"` (passthrough), `"legacy"` (always convert). **New in v4.1.** |
| `dashboard_healthy` | string | `"<green>"` | — | MiniMessage tag for HEALTHY status in `/vn servers`. Supports hex/RGB. **New in v4.1.** |
| `dashboard_draining` | string | `"<yellow>"` | — | MiniMessage tag for DRAINED status in `/vn servers`. **New in v4.1.** |
| `dashboard_open` | string | `"<red>"` | — | MiniMessage tag for CB_OPEN status in `/vn servers`. **New in v4.1.** |
| `dashboard_offline` | string | `"<gray>"` | — | MiniMessage tag for OFFLINE status in `/vn servers`. **New in v4.1.** |

> **Available placeholders in v4.1**: `<server>`, `<time>`, `<reason>`, `<mode>`, `<player>`, `<attempt>`, `<max>`. Not all placeholders are available in every message — see the table above for which ones apply to each message.

---

## `[routing]` — Core

Controls the selection algorithm and lobby pool.

```toml
[routing]
selection_mode = "least_players"
cycle_when_possible = true
balance_initial_join = true
default_lobbies = ["lobby-1", "lobby-2"]
max_retries = 2
```

| Setting | Type | Default | Accepted Values | Description |
|---------|------|---------|----------------|-------------|
| `selection_mode` | string | `"least_players"` | `least_players`, `round_robin`, `random`, `power_of_two`, `weighted_round_robin`, `least_connections`, `consistent_hash`, `latency` | The algorithm used to select a lobby. See [Routing Algorithms](Routing-Algorithms). |
| `cycle_when_possible` | boolean | `true` | — | Prevents routing a player to the same server they're already on. |
| `balance_initial_join` | boolean | `true` | — | Applies routing when players first connect to the proxy. |
| `default_lobbies` | LobbyEntry[] | `["lobby-1", "lobby-2"]` | See below | The pool of lobby servers. |
| `max_retries` | int | `2` | `0`–`10` | Number of retry attempts on connection failure. **New in v4.** |
| `use_chat_menu_for_lobby` | boolean | `false` | — | Use interactive chat selection menu for Java players. **New in v4.2.** |
| `chat_menu_header` | string | (see config) | — | Header of the Java interactive chat selector menu. **New in v4.2.** |
| `chat_menu_format` | string | (see config) | — | Format of each server button in Java chat selector. **New in v4.2.** |
| `chat_menu_tooltip` | string | (see config) | — | Tooltip displayed when hovering a server button. **New in v4.2.** |

### LobbyEntry Format

Each lobby entry can be a **plain string** or an **inline table**:

**Plain string** (backward compatible):
```toml
default_lobbies = ["lobby-1", "lobby-2"]
```

**Inline table** (v4 — adds max_players and weight):
```toml
default_lobbies = [
  { server = "lobby-1", max_players = 100, weight = 3 },
  { server = "lobby-2", max_players = 50, weight = 1 },
  "lobby-3",  # mixing is fine — this uses defaults
]
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `server` | string | (required) | Server name — must match `velocity.toml`. |
| `max_players` | int | `-1` (uncapped) | Maximum players before the server is considered "full" and skipped. `-1` = no limit. |
| `weight` | int | `1` | Relative weight for `weighted_round_robin`. Higher = more traffic. Only used by WRR. |

> **Tip**: You can mix plain strings and inline tables. Plain strings use `max_players = -1` (uncapped) and `weight = 1`.

---

## `[circuit_breaker]`

Automatic server failure detection. When a server fails repeated health checks, the circuit breaker opens and that server is skipped until it recovers.

```toml
[circuit_breaker]
enabled = true
failure_threshold = 3
cooldown_seconds = 30
half_open_max_tests = 1
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether the circuit breaker is active. |
| `failure_threshold` | int | `3` | Consecutive failures before the circuit opens (server is excluded). |
| `cooldown_seconds` | int | `30` | Seconds before an OPEN circuit transitions to HALF_OPEN (allows test requests). |
| `half_open_max_tests` | int | `1` | Number of test requests allowed in HALF_OPEN state before deciding to close or re-open. |

### How It Works

```
CLOSED ──(failures ≥ threshold)──► OPEN
   ▲                                 │
   │                         (cooldown elapses)
   │                                 ▼
   └──(test requests succeed)── HALF_OPEN
                                    │
                          (test requests fail)
                                    │
                                    ▼
                                  OPEN
```

- **CLOSED**: Normal operation. Failures are tracked.
- **OPEN**: Server is excluded from routing. No traffic sent.
- **HALF_OPEN**: A limited number of test requests are allowed. If they succeed → CLOSED. If they fail → OPEN again.

---

## `[degradation]`

Graceful degradation when all health checks fail. Instead of showing "No lobby found", falls back to selecting from configured lobbies using a simpler mode that ignores health status.

```toml
[degradation]
enabled = true
mode = "random"
```

| Setting | Type | Default | Accepted Values | Description |
|---------|------|---------|----------------|-------------|
| `enabled` | boolean | `true` | — | Whether degradation mode is active. |
| `mode` | string | `"random"` | `random`, `round_robin` | Algorithm used when degrading. `random` is recommended for safety. |

> **When this triggers**: Only when ALL candidate servers fail health checks. If even one server is healthy, normal routing continues.

---

## `[routing.affinity]`

Player affinity (sticky sessions) ensures players preferentially return to the same lobby they were last connected to during their proxy session. In v4.1.0, this is fully configurable under the `[routing.affinity]` TOML section.

```toml
[routing.affinity]
enabled = true
stickiness = 0.7
```

| Setting | Type | Default | Accepted Values | Description |
|---------|------|---------|----------------|-------------|
| `enabled` | boolean | `true` | — | Whether player affinity is active. |
| `stickiness` | double | `0.7` | `0.0`–`1.0` | Probability factor for session stickiness. `0.7` means a 70% chance of returning to the previous lobby and a 30% chance of running normal routing. |

> **How it works**: When a player runs the lobby command, VelocityNavigator checks if they have a saved session affinity record. If stickiness is set to `0.7`, there is a 70% chance they are immediately routed to their previous lobby (provided it is online and healthy), and a 30% chance the global selection algorithm is run.
> 
> **Important Notes**:
> - Session affinity records are stored in memory and are automatically cleaned up when a player disconnects from the proxy.
> - Player affinity is naturally bypassed when using the `consistent_hash` mode, as consistent hashing provides its own deterministic, hash-based player stickiness.
> - If the player's stickied lobby goes offline or trips the circuit breaker, the affinity system will safely skip it and route the player using the standard active algorithm.

---

## `[geo_routing]`

> ⚠️ **Experimental** — This feature is a stub in v4.1.0 and requires a GeoLite2 database to function. Without the database, geo-routing is silently skipped.

Geo-based routing sends players to lobbies closest to their geographic location.

```toml
[geo_routing]
enabled = false
database_path = ""
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether geo-routing is active. |
| `database_path` | string | `""` | Path to the GeoLite2-Country.mmdb file. Relative to the plugin directory. Example: `"GeoLite2-Country.mmdb"`. |

> **Config key note**: The config section is `[geo_routing]` (with underscore), not `[routing.geo]`.

See [Database Setup](GeoIP-Database-Setup) for step-by-step instructions.

---

## `[routing.contextual]`

Context-aware routing maps players leaving specific game servers to dedicated lobby pools.

```toml
[routing.contextual]
enabled = false
fallback_to_default = true

[routing.contextual.groups.bedwars_lobbies]
servers = [
  { server = "bw-lobby-1", weight = 2 },
  { server = "bw-lobby-2", weight = 1 },
]
mode = "consistent_hash"

[routing.contextual.groups.survival_lobbies]
servers = ["surv-hub-1", "surv-hub-2"]

[routing.contextual.sources]
"bedwars-1" = "bedwars_lobbies"
"bedwars-2" = "bedwars_lobbies"
"survival-1" = "survival_lobbies"

[routing.contextual.fallback_chain]
bedwars_lobbies = ["survival_lobbies"]
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether contextual routing is active. |
| `fallback_to_default` | boolean | `true` | Fall back to `default_lobbies` when no contextual group matches. |
| `groups` | map | — | Named groups of lobby servers. Each group can have `servers` (LobbyEntry[]) and optional `mode`. |
| `sources` | map | — | Maps source server names to group names. |
| `fallback_chain` | map | — | Maps group names to ordered lists of fallback group names. |

### Per-Group Selection Mode Override

Each group can specify its own `mode`, overriding the global `selection_mode`:

```toml
[routing.contextual.groups.bedwars_lobbies]
servers = ["bw-1", "bw-2"]
mode = "consistent_hash"   # Overrides global selection_mode for this group
```

If `mode` is omitted, the global `selection_mode` is used.

### Fallback Chain

When a group's servers are all unavailable, the fallback chain determines which groups to try next:

```toml
[routing.contextual.fallback_chain]
bedwars_lobbies = ["survival_lobbies"]
survival_lobbies = ["bedwars_lobbies"]
```

The chain is walked in order. If no fallback group has available servers and `fallback_to_default = true`, the default lobby pool is used as a last resort.

See [Contextual Routing Guide](Contextual-Routing-Guide) for a full tutorial.

---

## `[health_checks]`

Controls how the proxy monitors backend server availability. When health checks are enabled, VelocityNavigator pings backend servers and uses **live player counts** from `RegisteredServer.getPlayersConnected()` for routing decisions, ensuring accurate load balancing even when cache entries are stale.

```toml
[health_checks]
enabled = true
timeout_ms = 2500
cache_seconds = 60
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether health checks are performed. |
| `timeout_ms` | int | `2500` | Milliseconds to wait for a ping response before considering a server unhealthy. |
| `cache_seconds` | int | `60` | How long health check results are cached before re-checking. |

---

## `[update_checker]`

Controls the update checker. In v4, the update checker runs a **one-time check on startup** and can be triggered manually with `/vn updatecheck`.

```toml
[update_checker]
channel = "release"
```

| Setting | Type | Default | Accepted Values | Description |
|---------|------|---------|----------------|-------------|
| `channel` | string | `"release"` | `release`, `beta`, `alpha` | Which release channel to check against. |

**v4 changes**:
- `enabled` field removed — the checker always runs on startup (once, 5 seconds after proxy start)
- `notifyConsole` field removed — update notifications are always logged to console
- `startupDelaySeconds` field removed — fixed at 5 seconds
- Use `/vn updatecheck` to manually check for updates at any time

> **Tip**: Set `notify_on_startup = false` to suppress the startup notification. Set `notify_admins_on_join = false` to suppress in-game admin notifications.

---

## `[startup]` — First-Run Experience

> **New in v4.1.0** — Controls the welcome dashboard and upgrade digest shown in console on plugin start.

```toml
[startup]
welcome_enabled = true
wiki_url = "https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki"
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `welcome_enabled` | boolean | `true` | Show the "Getting Started" dashboard on fresh install and release notes digest on upgrades. |
| `wiki_url` | string | `"https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki"` | URL used for wiki anchor links in self-documenting config comments and welcome messages. |

---

## `[bedrock]` — Bedrock/Geyser Support

> **New in v4.1.0** — Configure Bedrock player routing support via Geyser and Floodgate.

```toml
[bedrock]
enabled = false
auto_detect = true
strip_advanced_formatting = true
affinity_use_java_uuid = true
use_gui_for_lobby = false
gui_title = "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient>"
gui_content = "<gray>Select a lobby server to connect:</gray>"
gui_button_format = "<white><bold>{server}</bold></white> <gray>({players} Players)</gray>"
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable Bedrock/Geyser support manually. |
| `auto_detect` | boolean | `true` | Auto-detect Geyser/Floodgate on the classpath to enable automatically. |
| `strip_advanced_formatting` | boolean | `true` | Strip gradients, hover actions, and click events from messages for clean Bedrock display. |
| `affinity_use_java_uuid` | boolean | `true` | Use Floodgate-mapped Java UUIDs instead of Bedrock XUIDs for player affinity tracking. |
| `use_gui_for_lobby` | boolean | `false` | Enable native Cumulus SimpleForm lobby selector menu for Bedrock players. **New in v4.2.** |
| `gui_title` | string | `"<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient>"` | Title of the Bedrock Form GUI. **New in v4.2.** |
| `gui_content` | string | `"<gray>Select a lobby server to connect:</gray>"` | Content text of the Bedrock Form GUI. **New in v4.2.** |
| `gui_button_format` | string | `"<white><bold>{server}</bold></white> <gray>({players} Players)</gray>"` | Button format of the Bedrock Form GUI. **New in v4.2.** |

---

## `[lobby]` — Empty Lobby Strategy

> **New in v4.1.0** — Configures behavior when no lobby servers are available for routing.

```toml
[lobby]
no_server_strategy = "disconnect"
no_server_message = "<red>No lobby servers are currently available. Please try again later.</red>"
fallback_server = ""
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `no_server_strategy` | string | `"disconnect"` | Strategy when no lobbies are online: `"disconnect"` (show message and disconnect) or `"fallback_server"` (route to a specific fallback server). |
| `no_server_message` | string | `"<red>No lobby servers are currently available...</red>"` | The disconnect message shown when using the `"disconnect"` strategy. |
| `fallback_server` | string | `""` | Server name to route to when using the `"fallback_server"` strategy. |

---

## `[debug]` and Top-Level Settings

```toml
notify_on_startup = true
notify_admins_on_join = true

[debug]
verbose_logging = false
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `notify_on_startup` | boolean | `true` | Whether to run an update check and log to console when the proxy starts. |
| `notify_admins_on_join` | boolean | `true` | Whether to notify admins with `velocitynavigator.admin` permission about available updates when they join the server. |
| `verbose_logging` | boolean | `false` | Enable debug-level logging for routing decisions. Located under `[debug]`, not a top-level setting. |

---

## Full Example Config

```toml
# VelocityNavigator v4.2.0 Configuration
# https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki

notify_on_startup = true
notify_admins_on_join = true

[commands]
primary = "lobby"
aliases = ["hub", "spawn"]
permission = "none"
admin_aliases = ["velocitynavigator", "vn"]
cooldown_seconds = 3
reconnect_if_same_server = false

[routing]
selection_mode = "power_of_two"
cycle_when_possible = true
balance_initial_join = true
max_retries = 2

default_lobbies = [
  { server = "lobby-1", max_players = 100, weight = 2 },
  { server = "lobby-2", max_players = 100, weight = 2 },
  { server = "lobby-3", max_players = 50, weight = 1 },
]

[routing.affinity]
enabled = true
stickiness = 0.7

[routing.contextual]
enabled = true
fallback_to_default = true

[routing.contextual.groups.bedwars_lobbies]
servers = ["bw-hub-1", "bw-hub-2"]
mode = "consistent_hash"

[routing.contextual.sources]
"bedwars-1" = "bedwars_lobbies"
"bedwars-2" = "bedwars_lobbies"

[routing.contextual.fallback_chain]
bedwars_lobbies = ["survival_lobbies"]

[health_checks]
enabled = true
timeout_ms = 2500
cache_seconds = 60

[messages]
connecting = "<aqua>Sending you to <server>...</aqua>"
alreadyConnected = "<yellow>You are already connected to <server>.</yellow>"
noLobbyFound = "<red>No available lobby could be found. (<reason>)</red>"
playerOnly = "<gray>This command can only be used by a player.</gray>"
cooldown = "<yellow>Please wait <time> more second(s).</yellow>"
reloadSuccess = "<green>VelocityNavigator reloaded.</green>"
reloadFailed = "<red>Reload failed. Check console for details.</red>"
retrying = "<yellow>Retrying connection... (<attempt>/<max>)</yellow>"
formatting = "auto"
dashboard_healthy = "<green>"
dashboard_draining = "<yellow>"
dashboard_open = "<red>"
dashboard_offline = "<gray>"

[update_checker]
enabled = true
channel = "release"
check_interval = 60
notify_admins = true

[metrics]
enabled = true

[metrics.prometheus]
enabled = false
port = 9225
bindHost = "127.0.0.1"
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable standard bStats metrics. |
| `prometheus.enabled` | boolean | `false` | Enable embedded Prometheus metrics server. **New in v4.2.** |
| `prometheus.port` | int | `9225` | Port to expose `/metrics` endpoint. **New in v4.2.** |
| `prometheus.bindHost` | string | `"127.0.0.1"` | Host/IP to bind the metrics server. **New in v4.2.** |

[circuit_breaker]
enabled = true
failure_threshold = 3
cooldown_seconds = 30
half_open_max_tests = 1

[degradation]
enabled = true
mode = "random"

[geo_routing]
enabled = false
database_path = ""

[startup]
welcome_enabled = true
wiki_url = "https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki"

[bedrock]
enabled = false
auto_detect = true
strip_advanced_formatting = true
affinity_use_java_uuid = true

[lobby]
no_server_strategy = "disconnect"
no_server_message = "<red>No lobby servers are currently available. Please try again later.</red>"
fallback_server = ""

[debug]
verbose_logging = false
```

---

→ **Next**: [Contextual Routing Guide](Contextual-Routing-Guide) | [Migration Guide v3 → v4](Migration-Guide-v3-to-v4)
