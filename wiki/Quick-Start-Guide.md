# Quick Start Guide

> Get VelocityNavigator running in under 10 minutes — no prior experience needed.

---

## Step 1: Download and Install

1. Download `VelocityNavigator-4.0.0.jar` from the [Releases](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/releases) page
2. Place the JAR in your Velocity proxy's `plugins/` folder
3. Restart the proxy (or run `/vn reload` if you're already running an older version)

```
plugins/
├── VelocityNavigator-4.0.0.jar
└── ...
```

On first start, VelocityNavigator generates `plugins/velocitynavigator/navigator.toml` with sensible defaults.

---

## Step 2: Watch It Work with Defaults

Right out of the box, VelocityNavigator uses:

- **Selection mode**: `least_players` (picks the server with the fewest players)
- **Lobbies**: whatever you defined in `velocity.toml`'s `try` list
- **Circuit breaker**: enabled (automatically skips unhealthy servers)
- **Health checks**: enabled with 60-second cache

If your `velocity.toml` already lists `lobby-1` and `lobby-2`, players typing `/lobby` will be routed to the emptier one automatically.

---

## Step 3: Edit `navigator.toml`

Open `plugins/velocitynavigator/navigator.toml` and set your lobby server names:

```toml
[routing]
selection_mode = "least_players"
default_lobbies = ["lobby-1", "lobby-2", "lobby-3"]
```

> **Important**: server names in `default_lobbies` must exactly match the server names defined in `velocity.toml`.

Save the file, then run `/vn reload` in the proxy console.

---

## Step 4: Choose a Selection Mode

Not sure which algorithm to use? Follow this decision tree:

```
How many lobby servers do you have?
│
├─ 1–3 servers ──── least_players   (simple, always picks emptiest)
│
├─ 4–10 servers ─── power_of_two    (fast, near-optimal distribution)
│
├─ 10+ servers ──── least_connections (EMA-based, handles bursty traffic)
│
└─ Need sticky sessions?
   │
   ├─ Yes ──── consistent_hash  (players return to "their" server)
   │
   └─ Need weighted distribution?
      │
      └─ Yes ──── weighted_round_robin  (some servers get more traffic)
```

| Mode | TL;DR |
|------|-------|
| `least_players` | Best for small networks. Picks the server with the fewest players. |
| `power_of_two` | Best default for medium networks. Picks two at random, chooses the emptier one. |
| `round_robin` | Strict rotation. Good for testing. |
| `random` | Each player gets a random server. Great at scale. |
| `weighted_round_robin` | Like round-robin but some servers get more traffic than others. |
| `least_connections` | Tracks connection rate over time. Good for bursty traffic. |
| `consistent_hash` | Same player → same server. Great for session affinity. |

See [Routing Algorithms](Routing-Algorithms) for the full deep-dive.

---

## Step 5: Test It

1. Join your network
2. Type `/lobby`
3. You should be connected to one of your lobby servers

Check the routing decision:

```
/vn debug player YourName
```

Verify distribution across your servers:

```
/vn status
```

---

## Next Steps

- 📖 [Configuration Guide](Configuration-Guide) — customize every setting
- 🔀 [Routing Algorithms](Routing-Algorithms) — understand how each algorithm works
- 🛠️ [Operations Runbook](Operations-Runbook) — drain servers, check health, troubleshoot

---

**Total time: ~5 minutes.** Welcome to VelocityNavigator v4! 🎉
