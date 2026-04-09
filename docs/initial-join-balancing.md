# Initial Join Balancing

> **New in v3.0.0** — Load-balance players the moment they connect to the proxy.

---

## The Problem

By default, Velocity uses a static `try` list in `velocity.toml` to decide which server a new player joins:

```toml
[servers]
try = ["lobby-1", "lobby-2"]
```

Velocity always tries the **first server** in the list. If `lobby-1` is online, *every single player* joins `lobby-1`. The second server is only used if the first one is offline. This means your second lobby sits empty while the first one fills up.

---

## The Solution

VelocityNavigator v3.0.0 intercepts the `PlayerChooseInitialServerEvent` and applies its routing brain **before** the player lands on any server. This means:

- **`least_players` mode**: The first player gets Lobby 1, the second gets Lobby 2, and so on based on real-time player counts.
- **`round_robin` mode**: Players alternate between lobbies in strict rotation.
- **`random` mode**: Each player gets a random lobby assignment.

---

## Configuration

```toml
[routing]
balance_initial_join = true
```

| Value | Behavior |
|-------|----------|
| `true` | VelocityNavigator's routing brain handles initial connections. Players are load-balanced immediately. |
| `false` | Velocity's native `try` list is used (default Velocity behavior). |

---

## When to Disable

You might want to set `balance_initial_join = false` if:
- You have a dedicated "welcome" server that all players must join first
- Another plugin already handles initial routing (e.g., a queue plugin)
- You prefer Velocity's native failover behavior for simplicity

---

## Technical Details

- The plugin subscribes to `PlayerChooseInitialServerEvent` (fires after `PostLoginEvent`)
- The routing decision is computed synchronously to ensure the result is applied before the event completes
- Health checks and contextual routing are fully applied during initial join
- When `verbose_logging = true`, every balanced initial join is logged: `[VelocityNavigator] Balanced initial join for PlayerX -> lobby-Y`
