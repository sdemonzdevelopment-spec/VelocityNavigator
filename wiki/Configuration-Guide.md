# VelocityNavigator Configuration Guide

> [!NOTE]  
> Full documentation for `navigator.toml` — every node, key, and array explained in extreme detail. 

---

## Global Concept

The configuration file `navigator.toml` is divided into discrete functional routing blocks:
1. `[commands]` — Controls what players type and what permissions are required.
2. `[routing]` — Controls the selection algorithm and lobby pool logic.
3. `[routing.contextual]` — Controls advanced sub-network routing (e.g., game-specific lobbies).
4. `[health_checks]` — Controls how the proxy monitors backend server availability.

---

## `[commands]` Structure

```toml
[commands]
primary = "lobby"
aliases = ["hub", "spawn"]
permission = "velocitynavigator.use"
admin_aliases = ["velocitynavigator", "vn"]
cooldown_seconds = 3
reconnect_if_same_server = false
```

| Setting | Type | Description |
|---------|------|-------------|
| `primary` | string | The main command players type (e.g. `/lobby`). |
| `aliases` | string[] | Alternative commands that map to the primary (`/hub`, `/spawn`). |
| `permission` | string | Set to `"none"` to allow all players bypassing auth plugins. |
| `cooldown_seconds` | int | Anti-spam cooldown in seconds, enforcing memory-level lockouts. |

---

## `[routing]` Core Matrix

```toml
[routing]
selection_mode = "least_players"
cycle_when_possible = true
balance_initial_join = true
default_lobbies = ["lobby-1", "lobby-2"]
```

> [!IMPORTANT]  
> The `default_lobbies` array must **exactly** match the downstream backend server definitions mapped in `velocity.toml`.

| Setting | Default | Description |
|---------|---------|-------------|
| `selection_mode` | `"least_players"` | See [Routing Algorithms](Routing-Algorithms) for deep details. |
| `cycle_when_possible` | `true` | Prevents a player typing `/lobby` from being dumped onto the exact same lobby they are already standing on currently. |
| `balance_initial_join` | `true` | Bypasses `velocity.toml` proxy fallback routing statically matching them to the algorithm the moment they join. |

---

## `[routing.contextual]` Mapping

Contextual routing maps players leaving **specific game servers** to **localized lobby pools**, instead of a global network hub.

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

If a player dies on `bedwars-1` and triggers the `/lobby` proxy hook, rather than returning to a chaotic global `hub`, they are seamlessly transitioned back to the `bedwars_lobbies` pool!
