# Routing Flow Algorithms

> Deep dive into how VelocityNavigator computes which server connection stream to assign.

---

## 🧭 Which Matrix Do I Set?

| Your Proxy Profile | Recommended | Why |
|--------------------|-------------|-----|
| **Vast majority of networks** | `least_players` | Mathematically guarantees the most perfect traffic distribution based on real-time connected players. |
| **High velocity environments** | `random` | Statistical math naturally forces convergence at scale without triggering massive ping-burst overhead arrays. |
| **Isolated test clusters** | `round_robin` | Strictly deterministic, predictable mathematical switching. |

---

## 1️⃣ Least Players (`least_players`)
> **"The load balancer"**

**How it works**: Pings the entire backend array and measures real-time cluster size. Traffic is instantly multiplexed directly to the emptiest online sub-node array.

**Formula**:
```text
Target Node = server with Min(PlayerCount) -> from Lobbies[]
```

---

## 2️⃣ Round Robin (`round_robin`)
> **"The sequential rotational dealer"**

**How it works**: Distributes joining players like dealing playing cards down a table, completely ignoring the backend node size or weight mechanics explicitly.

**Formula**:
```java
// Uses a threadsafe non-blocking AtomicInteger internally
Target Node = Lobbies[ Cursor.getAndIncrement() % TotalLobbies ]
```

---

## 3️⃣ Random Probability (`random`)
> **"The dice roll variance model"**

**How it works**: Randomly binds incoming packets to a randomized server instance. Very cheap computationally.

**When to use**: Enterprise-scale backend nodes registering hundreds of unique connections a minute. Over standard connection probability times, exact equal split variance distributions mathematically lock in.

---

> [!WARNING]  
> If an arbitrary lobby fails health-checks and timeouts natively, it is violently ejected from all 3 algorithms instantaneously and the math falls back onto computing the remaining network capacity!
