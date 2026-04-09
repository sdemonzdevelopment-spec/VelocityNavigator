# VelocityNavigator Routing Algorithms

> Deep dive into how VelocityNavigator decides which server to send your players to.

---

## Which Mode Should I Use?

| Your Network | Recommended Mode | Why |
|--------------|-----------------|-----|
| **Most networks** (2-10 lobbies) | `least_players` | Guarantees the most even distribution based on real-time player counts |
| **High-traffic networks** (500+ joins/min) | `random` | No ping overhead; statistical distribution naturally converges at scale |
| **Testing / Simple setups** | `round_robin` | Predictable, deterministic rotation without requiring health checks |

---

## 1. Least Players (`least_players`)
### The "Intelligent Load Balancer"

**How it works**: The plugin pings every candidate lobby in real-time to check how many players are currently sitting on each one. It then sends the next player to whichever lobby has the lowest count.

**Formula**:
```
Target = lobby with Min(PlayerCount) across all online lobbies
```

**Example — 4 players joining an empty 2-lobby network**:
| Player | Lobby 1 Count | Lobby 2 Count | Sent To |
|--------|--------------|--------------|---------|
| Player 1 | 0 | 0 | Lobby 1 (tie-break) |
| Player 2 | 1 | 0 | **Lobby 2** |
| Player 3 | 1 | 1 | Lobby 1 (tie-break) |
| Player 4 | 2 | 1 | **Lobby 2** |

**Result**: 2 players on Lobby 1, 2 on Lobby 2 — perfectly even.

**Example — Skewed network** (Lobby 1 has 80 players, Lobby 2 has 0):
All 4 players get sent to Lobby 2, aggressively rebalancing the load.

---

## 2. Round Robin (`round_robin`)
### The "Sequential Dealer"

**How it works**: Deals players out like a deck of cards in strict rotation. Uses a thread-safe `AtomicInteger` cursor. Completely ignores actual player counts.

**Formula**:
```
Target = LobbyList[ CursorIndex % TotalLobbies ]
Cursor++ after each selection
```

**Example — 4 players, 2 lobbies**:
| Player | Cursor | Cursor % 2 | Sent To |
|--------|--------|-----------|---------|
| Player 1 | 0 | 0 | Lobby 1 |
| Player 2 | 1 | 1 | **Lobby 2** |
| Player 3 | 2 | 0 | Lobby 1 |
| Player 4 | 3 | 1 | **Lobby 2** |

**Result**: Always alternates perfectly. But if 20 people leave Lobby 1, it won't notice and fill it back up quickly.

---

## 3. Random (`random`)
### The "Dice Roll"

**How it works**: Rolls a die to pick any available server at random.

**Formula**:
```
Target = LobbyList[ Random.nextInt(TotalLobbies) ]
```

**Example — 4 players, 2 lobbies**:
Result is **unpredictable**. You might get 3-1 or 4-0 or 2-2. Over thousands of connections, the distribution naturally converges to 50/50.

**When to use**: Enterprise-scale clusters with 500+ proxy connections per minute where statistical convergence is guaranteed and the ping overhead of `least_players` is undesirable.

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| **Only 1 lobby online** | All modes send to that single lobby. No balancing possible. |
| **All lobbies offline** | Player sees the "No available lobby could be found" error message. |
| **Player already on the chosen lobby** | If `cycle_when_possible = true`, the plugin picks the next-best option. If only one option exists, player stays and sees "You are already connected." |
| **Health check timeout** | The slow-responding lobby is excluded from the candidate pool for that routing decision. |
