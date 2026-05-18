# Frequently Asked Questions

> Quick answers to the most common questions about VelocityNavigator.

---

## General

### Which selection mode should I use?

**For most networks**: `power_of_two`. It's fast, produces near-optimal distribution, and works well from 4 servers up to hundreds.

**Specific cases**:
- **2–3 servers**: `least_players` (simplest, perfectly even)
- **Need weighted distribution**: `weighted_round_robin`
- **Need sticky sessions**: `consistent_hash`
- **Bursty traffic**: `least_connections`
- **Just testing**: `round_robin`

See [Routing Algorithms](Routing-Algorithms) for the full comparison.

---

### Do I need a database?

**No.** VelocityNavigator does **not** require MySQL, PostgreSQL, Redis, or any SQL/NoSQL database. All player data (affinity, cooldowns, circuit breaker state) is stored in memory within the proxy's JVM.

The only external data file is the optional GeoLite2 `.mmdb` file used for geo-routing. If you don't use geo-routing, you don't need any database at all.

See [Database Setup](GeoIP-Database-Setup) for full details.

---

### Do I need GeoIP?

**Only if** you have lobby servers in multiple geographic regions (e.g., one in the US and one in Europe). If all your servers are in one location, geo-routing provides no benefit.

Geo-routing is also **experimental** in v4.0.0 — it works but may not be production-stable for all setups.

See [Database Setup](GeoIP-Database-Setup) for setup instructions.

---

### What happens if all servers go down?

If all lobby servers fail health checks:
1. **With degradation enabled** (default): VelocityNavigator falls back to selecting from configured lobbies using the degradation mode (default: `random`), ignoring health status. Players will be sent to a server even if it might be down.
2. **With degradation disabled**: Players see the "No lobby found" message.

We recommend keeping degradation enabled:
```toml
[degradation]
enabled = true
mode = "random"
```

---

### Can I use different selection modes for different groups?

**Yes!** This is a v4 feature. Each contextual routing group can override the global `selection_mode`:

```toml
[routing.contextual.groups.bedwars_lobbies]
servers = ["bw-1", "bw-2"]
mode = "consistent_hash"  # Overrides global mode for this group
```

See [Contextual Routing Guide](Contextual-Routing-Guide) for the full tutorial.

---

### How do I take a server offline for maintenance?

Use the drain command:

```
/vn drain lobby-2
```

This prevents any new players from being routed to `lobby-2`. Existing players are not kicked. When maintenance is complete:

```
/vn undrain lobby-2
```

See [Operations Runbook](Operations-Runbook) for the full procedure.

---

### Does it work with Velocity 3.x only?

Yes, VelocityNavigator is built exclusively for Velocity 3.x. It requires Java 17 or higher.

It does **not** work with BungeeCord, Waterfall, or other proxy software.

---

### What's the difference between `least_players` and `least_connections`?

- **`least_players`**: Looks at the current player count and picks the server with the fewest players. Simple and accurate for steady-state traffic.
- **`least_connections`**: Uses an Exponential Moving Average (EMA) of connection rates and load over time. Better at handling **bursty** traffic where many players join simultaneously.

For most networks, `least_players` or `power_of_two` is sufficient. Use `least_connections` if you experience traffic spikes.

---

### How do I make players always return to the same lobby?

Use `consistent_hash` mode — it hashes the player's UUID to deterministically assign them to a server:

```toml
[routing]
selection_mode = "consistent_hash"
```

Or combine with **player affinity** for even stronger stickiness. In v4.0.0, player affinity is always active with a 70% stickiness factor — meaning there's a 70% chance players return to their previous lobby. This works alongside any selection mode:

```toml
[routing]
selection_mode = "power_of_two"
# Affinity is always active (0.7 stickiness) — no config needed
```

> **Note**: Affinity configuration (stickiness tuning, enable/disable) may be exposed as a `[routing.affinity]` config section in a future release. In v4.0.0, it's hardcoded at 0.7 stickiness.

---

### How do I set different player caps for different servers?

Use the LobbyEntry inline table format with `max_players`:

```toml
default_lobbies = [
  { server = "lobby-big", max_players = 200 },
  { server = "lobby-small", max_players = 50 },
  "lobby-default",  # uncapped (-1)
]
```

When a server reaches its `max_players` cap, it's excluded from routing.

---

### Can I mix plain strings and inline tables in `default_lobbies`?

**Yes!** Plain strings use default values (`max_players = -1`, `weight = 1`):

```toml
default_lobbies = [
  { server = "lobby-1", max_players = 100, weight = 3 },
  "lobby-2",   # max_players = -1 (uncapped), weight = 1
  { server = "lobby-3", weight = 2 },
]
```

---

### What happened to the auto-update checker?

In v4, the recurring auto-update checker was replaced with:
1. A **one-time startup check** — runs 5 seconds after the proxy starts
2. A **manual check command**: `/vn updatecheck`
3. An **admin join notification** — when a player with `velocitynavigator.admin` permission joins, if an update is available, they receive a chat message

The old `update_checker.enabled`, `notifyConsole`, and `startupDelaySeconds` fields have been removed. To suppress the startup notification, set `notify_on_startup = false`. To suppress the admin join notification, set `notify_admins_on_join = false`.

---

### What's the new permission node?

| v3 | v4 | Status |
|----|----|--------|
| `velocitynavigator.bypasscooldown` | `velocitynavigator.bypass.cooldown` | **Both work.** The v3 name is checked as a fallback. |

Update your permission plugin when convenient, but nothing will break.

---

### How do I check if the circuit breaker has opened?

```
/vn debug server lobby-1
```

Look for the `Circuit breaker` line in the output. States: `CLOSED` (healthy), `OPEN` (excluded), `HALF_OPEN` (testing).

Or check all at once:
```
/vn status
```

---

### Does VelocityNavigator support multiple proxies?

Yes, with caveats:
- `round_robin` and `weighted_round_robin` maintain state **per proxy instance**. If you have multiple proxies behind a load balancer, each proxy has its own counter.
- `random`, `power_of_two`, `least_players`, and `consistent_hash` are stateless and work correctly across multiple proxies.
- Player affinity is stored **in memory per proxy**. If a player connects to a different proxy, their affinity record won't be available.

For multi-proxy setups, we recommend `power_of_two` or `random` for the most consistent behavior.

---

→ See also: [Configuration Guide](Configuration-Guide) | [Routing Algorithms](Routing-Algorithms) | [Operations Runbook](Operations-Runbook)
