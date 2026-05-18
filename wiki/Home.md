<p align="center">
  <img src="https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/hero-banner.png" alt="VelocityNavigator Banner">
</p>

# VelocityNavigator

> `v4.0.0` &nbsp;·&nbsp; Velocity 3.x &nbsp;·&nbsp; Java 17+

**VelocityNavigator** is an intelligent lobby routing plugin for Velocity proxies. It distributes players across your lobby servers using real-time load balancing — so no single server bears the entire load. With seven selection algorithms, circuit breaker protection, player affinity, and contextual routing, it handles everything from a two-server hobby network to a hundred-node infrastructure without breaking a sweat.

---

## ✨ Feature Highlights

- 🧠 **7 Selection Algorithms** — `least_players`, `round_robin`, `random`, `power_of_two`, `weighted_round_robin`, `least_connections`, `consistent_hash` — pick the one that fits, or use different modes per group
- ⚡ **Initial Join Balancing** — players are load-balanced the moment they connect, not just when they type `/lobby`
- 🛡️ **Circuit Breaker** — automatic failure detection with CLOSED → OPEN → HALF_OPEN state machine; unhealthy servers are skipped until they recover
- 🔀 **Contextual Routing** — route players to game-specific lobbies based on which server they're leaving, with per-group selection modes and fallback chains
- 💾 **Player Affinity** — sticky sessions so players tend to return to the same lobby they were on before
- 🔧 **Server Drain Mode** — gracefully take servers offline for maintenance with `/vn drain`; no players are routed to drained servers
- 🔄 **Connection Retry** — automatic retry with fallback on connection failure, so a single dead server doesn't strand a player
- 📊 **Routing Metrics API** — monitor distribution, health check latencies, and circuit breaker states programmatically

---

## 🚀 Getting Started

1. **Install** — Drop the JAR into your Velocity proxy's `plugins/` folder and start the proxy
2. **Edit** — Open `plugins/velocitynavigator/navigator.toml` and set your lobby server names
3. **Play** — Type `/lobby` in-game. You're done.

→ **[Quick Start Guide](Quick-Start-Guide)** — step-by-step walkthrough (under 10 minutes)

---

## 📖 Documentation

| Section | Page |
|---------|------|
| Getting Started | [Quick Start Guide](Quick-Start-Guide) |
| Routing Algorithms | [Routing Algorithms](Routing-Algorithms) · [Visualizations](Algorithm-Visualizations) |
| Configuration | [Configuration Guide](Configuration-Guide) · [Migration v3 → v4](Migration-Guide-v3-to-v4) |
| Contextual & Geo Routing | [Contextual Routing Guide](Contextual-Routing-Guide) · [Database Setup](GeoIP-Database-Setup) |
| Features | [Initial Join Balancing](Initial-Join-Balancing) |
| Operations | [Operations Runbook](Operations-Runbook) · [Troubleshooting](Troubleshooting-Guide) · [FAQ](FAQ) |
| Developer | [Developer API](Developer-API) |

---

## 🔗 Compatibility

| Requirement | Version |
|-------------|---------|
| Velocity | 3.x |
| Java | 17+ |
