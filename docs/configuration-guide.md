# VelocityNavigator Configuration Guide

> Full documentation for `navigator.toml` — every setting explained in detail.
> 
> **Plugin**: VelocityNavigator v3.0.0  
> **Author**: DemonZ Development

---

## Global Structure

The configuration file `navigator.toml` is divided into these sections:

1. `[commands]` — Controls what players type and what permissions are required
2. `[routing]` — Controls the selection algorithm and lobby pool logic
3. `[routing.contextual]` — Controls advanced sub-network routing (e.g., game-specific lobbies)
4. `[health_checks]` — Controls how the proxy monitors backend server availability
5. `[messages]` — Customizable player-facing messages with MiniMessage formatting
6. `[update_checker]` — Automatic version checking via Modrinth
7. `[metrics]` — bStats telemetry toggle
8. `[debug]` — Verbose logging for diagnostics

---

## Section: commands

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
| `aliases` | string[] | `["hub", "spawn"]` | Alternative commands that map to the primary. `/hub` and `/spawn` will behave identically to `/lobby`. |
| `permission` | string | `"velocitynavigator.use"` | Permission node required to use the command. Set to `"none"` to allow all players. Admins with `velocitynavigator.admin` bypass this automatically. |
| `admin_aliases` | string[] | `["velocitynavigator", "vn"]` | Admin command aliases. The first entry becomes the primary admin command. |
| `cooldown_seconds` | int | `3` | Anti-spam cooldown in seconds. Protected against macro-based abuse with pre-execution locking. |
| `reconnect_if_same_server` | bool | `false` | If `true`, a player already on `lobby-1` who gets assigned `lobby-1` again will be forcefully re-connected (causing a brief loading screen). Usually kept `false`. |

---

## Section: routing

```toml
[routing]
selection_mode = "least_players"
cycle_when_possible = true
balance_initial_join = true
default_lobbies = ["lobby-1", "lobby-2"]
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `selection_mode` | string | `"least_players"` | The core routing algorithm. See [Routing Algorithms](routing-algorithms.md) for details. |
| `cycle_when_possible` | bool | `true` | When `true`, the plugin tries to avoid sending a player to the server they are already on. If only one server exists, this gracefully falls back. |
| `balance_initial_join` | bool | `true` | **NEW in v3.** When `true`, the plugin's routing brain handles initial proxy connections instead of Velocity's static `try` list. Players are load-balanced the moment they connect, not just when they run `/lobby`. |
| `default_lobbies` | string[] | `["lobby-1", "lobby-2"]` | Backend server names that form the default lobby pool. Must exactly match names in `velocity.toml` under `[servers]`. Case-insensitive. |

### Available Routing Modes

| Mode | Description | Best For |
|------|-------------|----------|
| `least_players` | Sends players to the lobby with the fewest players. Pings servers in real-time. | Even distribution, most networks |
| `round_robin` | Sends players in strict rotation: Lobby 1 → Lobby 2 → Lobby 1 → Lobby 2. Ignores player counts. | Consistent rotation without ping overhead |
| `random` | Picks a random lobby from the pool. | Very large networks (500+ joins/min) where statistical convergence is natural |

---

## Section: routing.contextual

```toml
[routing.contextual]
enabled = false
fallback_to_default = true

[routing.contextual.groups]
bedwars_lobbies = ["bw-lobby-1", "bw-lobby-2"]

[routing.contextual.sources]
"bedwars-1" = "bedwars_lobbies"
"bedwars-2" = "bedwars_lobbies"
```

Contextual routing maps players leaving **specific game servers** to **localized lobby pools**, instead of a global network hub.

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | bool | `false` | Master toggle for contextual routing. |
| `fallback_to_default` | bool | `true` | If all servers in the contextual group are offline, fall back to `default_lobbies` instead of showing an error. |
| `groups` | map | `{}` | Named lobby pools. Each key is a group name, and the value is a list of server names. |
| `sources` | map | `{}` | Maps game servers to groups. If a player on `bedwars-1` types `/lobby`, they get routed to the `bedwars_lobbies` group. |

---

## Section: health_checks

```toml
[health_checks]
enabled = true
timeout_ms = 2500
cache_seconds = 60
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | bool | `true` | Ping candidate lobbies before routing to verify they are alive. |
| `timeout_ms` | int | `2500` | Network timeout in milliseconds. If a lobby takes longer than this to respond, it is considered offline and excluded from routing. |
| `cache_seconds` | int | `60` | Cache health check results for this many seconds. Multiple simultaneous `/lobby` commands share the same `CompletableFuture` ping (ping coalescing). Set to `0` to disable caching. |

---

## Section: messages

```toml
[messages]
connecting = "<aqua>Sending you to <server>...</aqua>"
already_connected = "<yellow>You are already connected to <server>.</yellow>"
no_lobby_found = "<red>No available lobby could be found.</red>"
player_only = "<gray>This command can only be used by a player.</gray>"
cooldown = "<yellow>Please wait <time> more second(s).</yellow>"
reload_success = "<green>VelocityNavigator reloaded.</green>"
reload_failed = "<red>Reload failed. Check console for details.</red>"
```

All messages support [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. Available placeholders:

| Placeholder | Available In | Description |
|-------------|-------------|-------------|
| `<server>` | `connecting`, `already_connected` | The target server name |
| `<time>` | `cooldown` | Remaining cooldown seconds |

---

## Section: update_checker

```toml
[update_checker]
enabled = true
channel = "release"
notify_console = true
startup_delay_seconds = 5
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | bool | `true` | Check Modrinth for updates during startup. |
| `channel` | string | `"release"` | Which release channel to check: `release`, `beta`, or `alpha`. |
| `notify_console` | bool | `true` | Print update notifications to the console. |
| `startup_delay_seconds` | int | `5` | Delay before the first update check (avoids slowing down proxy boot). |

---

## Section: metrics

```toml
[metrics]
enabled = true
```

Enables [bStats](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341) telemetry. Sends anonymous usage data (routing mode, lobby count, Java version) to help the developer understand how the plugin is used. Can be disabled if your network policy requires it.

---

## Section: debug

```toml
[debug]
verbose_logging = false
```

When `true`, adds detailed routing diagnostics to the console for every `/lobby` command and initial join event. Useful for debugging routing decisions in development.
