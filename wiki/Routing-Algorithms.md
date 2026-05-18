# Routing Algorithms

> Deep dive into how VelocityNavigator decides which server to send a player to.

---

## Comparison Table

| Algorithm | Distribution Quality | CPU Cost | Requires Health Data | Sticky Sessions | Best For |
|-----------|---------------------|----------|---------------------|-----------------|----------|
| `least_players` | ★★★★★ | Medium | Yes | No | Small-medium networks |
| `power_of_two` | ★★★★☆ | Low | Yes | No | Medium networks, default pick |
| `round_robin` | ★★★☆☆ | Very Low | No | No | Testing, strict fairness |
| `random` | ★★★☆☆ | Very Low | No | No | Large-scale networks |
| `weighted_round_robin` | ★★★★☆ | Low | No | No | Unequal server capacity |
| `least_connections` | ★★★★★ | Medium | Yes | No | Bursty traffic, large networks |
| `consistent_hash` | ★★★☆☆ | Low | No | Yes | Session affinity, party routing |

---

## 1️⃣ Least Players (`least_players`)

> Picks the server with the fewest connected players.

**Complexity**: O(n) — scans all candidates each selection.

**When to use**: Most networks. This is the default selection mode because it produces the most even distribution when you have a small-to-medium number of servers.

**When NOT to use**: Very large server pools (50+) where scanning every server adds measurable latency, or when you need deterministic player-to-server mapping.

> **Note**: While `least_players` is the code default, `power_of_two` is the recommended default for most production networks (see below).

**Example (10 players → 3 servers)**:
```
lobby-1: ████ (4 players)
lobby-2: ███  (3 players)
lobby-3: ███  (3 players)
```

---

## 2️⃣ Power of Two (`power_of_two`)

> Picks two random candidates, then selects the one with fewer players.

**Complexity**: O(1) — only examines two servers.

**When to use**: Medium-sized networks (4–10 servers). Provides near-optimal distribution at a fraction of the cost of `least_players`. This is the recommended default for most production networks.

**When NOT to use**: Very small networks (2 servers — it degenerates to `least_players`) or when you need perfectly even distribution.

**Example (10 players → 3 servers)**:
```
lobby-1: ████ (4 players)
lobby-2: ███  (3 players)
lobby-3: ███  (3 players)
```
*Nearly identical to `least_players` at low load, but scales much better.*

---

## 3️⃣ Round Robin (`round_robin`)

> Cycles through servers in strict order using an atomic counter.

**Complexity**: O(1) — no scanning, just increment and modulo.

**When to use**: Testing, benchmarking, or when you need perfectly deterministic rotation. Good when all servers have identical capacity.

**When NOT to use**: Production networks where servers have different capacities, or when players join in bursts (causes temporary imbalance).

**Example (10 players → 3 servers)**:
```
lobby-1: ████ (4 players)  ← players 1, 4, 7, 10
lobby-2: ███  (3 players)  ← players 2, 5, 8
lobby-3: ███  (3 players)  ← players 3, 6, 9
```

---

## 4️⃣ Random (`random`)

> Each player is assigned a completely random server.

**Complexity**: O(1) — single random selection.

**When to use**: Very large networks (50+ servers). At scale, the law of large numbers guarantees roughly even distribution. Zero coordination overhead between proxy instances.

**When NOT to use**: Small networks where random variance produces noticeable imbalance, or when you need any kind of deterministic behavior.

**Example (10 players → 3 servers)**:
```
lobby-1: █████ (5 players)  ← random variance
lobby-2: ███  (3 players)
lobby-3: ██   (2 players)
```
*Variance evens out as player count grows.*

---

## 5️⃣ Weighted Round Robin (`weighted_round_robin`)

> Like round-robin, but servers with higher weight receive proportionally more players. Uses interleaved WRR to avoid burst clustering.

**Complexity**: O(n) per round cycle, O(1) amortized per selection.

**When to use**: When your servers have different capacities. Set `weight` higher on beefier servers so they receive more traffic.

**When NOT to use**: When all servers are identical (just use regular `round_robin` or `power_of_two`).

**Example (10 players → 3 servers, weights: lobby-1=3, lobby-2=2, lobby-3=1)**:
```
lobby-1: █████ (5 players)  ← weight 3
lobby-2: ███  (3 players)  ← weight 2
lobby-3: ██   (2 players)  ← weight 1
```

Configure weights using inline table format:

```toml
default_lobbies = [
  { server = "lobby-1", weight = 3 },
  { server = "lobby-2", weight = 2 },
  { server = "lobby-3", weight = 1 },
]
```

---

## 6️⃣ Least Connections (`least_connections`)

> Selects the server with the lowest exponential moving average (EMA) of active connections and connection rate over time.

**Complexity**: O(n) — scans all candidates with EMA computation.

**When to use**: Networks with bursty traffic patterns. EMA smooths out momentary spikes, making this more stable than `least_players` during traffic surges.

**When NOT to use**: Very small or very stable networks where `least_players` or `power_of_two` are simpler and equally effective.

**Example (10 players → 3 servers, with burst traffic)**:
```
lobby-1: ███  (3 players)  ← EMA low, receives next player
lobby-2: ████ (4 players)  ← EMA elevated from recent burst
lobby-3: ███  (3 players)  ← EMA low
```

---

## 7️⃣ Consistent Hash (`consistent_hash`)

> Hashes the player's UUID onto a consistent hash ring (150 virtual nodes, SHA-256). The same player always lands on the same server unless that server is removed.

**Complexity**: O(log n) — ring lookup.

**When to use**: When you need **sticky sessions** — players returning to "their" server. Great for party routing, inventory caching, or any system where player-server affinity matters.

**When NOT to use**: When you need perfectly even distribution (hash distribution has natural variance), or when you don't need sticky sessions.

**Example (10 players → 3 servers)**:
```
lobby-1: ████ (4 players)  ← hash ring assignment
lobby-2: ███  (3 players)
lobby-3: ███  (3 players)
```
*Same player always goes to the same server. Adding/removing servers only remaps a fraction of players.*

---

## Health Check Integration

All algorithms that require real-time data (`least_players`, `power_of_two`, `least_connections`) rely on player count information. VelocityNavigator uses **live player counts** from `RegisteredServer.getPlayersConnected()` for routing decisions, ensuring accurate and up-to-date load information.

The health check cache serves as an **online/offline filter** — servers marked as offline by health checks are excluded from the candidate pool. Health checks run on a configurable interval (default: 60 seconds) with a cache warming task that runs at 80% of the TTL to ensure data freshness.

When the **circuit breaker** opens for a server (after repeated failures), that server is also excluded — even if its health check cache hasn't expired yet.

---

## Graceful Degradation

When **all** candidate servers fail health checks and no selection can be made, VelocityNavigator can fall back to a **degradation mode** (default: `random`) that ignores health status and selects from all configured lobbies. This prevents the "No lobby found" error during total outages.

```toml
[degradation]
enabled = true
mode = "random"
```

See [Configuration Guide](Configuration-Guide) for details.

---

## Per-Group Overrides

Contextual routing groups can override the global selection mode. For example, your main lobbies use `power_of_two` but your BedWars lobbies use `consistent_hash` so players return to the same lobby:

```toml
[routing.contextual.groups.bedwars_lobbies]
servers = ["bw-1", "bw-2"]
mode = "consistent_hash"
```

See [Contextual Routing Guide](Contextual-Routing-Guide) for the full tutorial.
