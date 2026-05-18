# VelocityNavigator Spigot Resource Copy

![VelocityNavigator Banner](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/hero-banner.png)

## Title

VelocityNavigator | Production-Grade Lobby Routing & Initial Join Load Balancing for Velocity

## Short Description

The smartest lobby system for Velocity proxies — true initial join load balancing, health-checked routing, contextual groups, and live debug tools.

## Important Platform Note

> **This is a Velocity-only plugin.** It does not run on Bukkit, Spigot, or Paper backends.
> This Spigot listing exists for visibility and download discovery. Install the JAR on your **Velocity proxy**, not your backend servers.

![Routing](https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/feature-routing.png)

## 🚀 v3.0.0 Feature Highlights

- **Initial Join Balancing:** Load-balance players the absolute millisecond they hit the proxy, without them ever needing to type `/lobby`.
- **Three routing strategies:** `least_players`, `random`, `round_robin`
- **Contextual groups:** different game servers route to different lobby pools (e.g., bedwars games → bedwars lobbies)
- **Automatic fallback:** if a contextual group goes offline, routing falls back to default lobbies

## Network Resilience
- **Async health checks** with configurable timeout and TTL caching
- **Ping coalescing** — 50 players connecting at once fires ONE ping per backend server, not 50 (prevents network storms)
- **Pre-execution cooldown locks** — macro spam is blocked before routing even starts

## Live Diagnostics
- `/vn reload` — reload config without proxy restart
- `/vn status` — runtime dashboard (routing mode, lobbies, health checks, metrics)
- `/vn version` — installed version + latest version fetcher
- `/vn debug player <name>` — preview exactly how the routing engine resolves a specific player
- `/vn debug server <name>` — inspect a server's health check state

## Installation

1. Install Velocity 3.x on your proxy
2. Drop the VelocityNavigator JAR into `plugins/`
3. Start the proxy once — a heavily-commented config is auto-generated
4. Edit `plugins/velocitynavigator/navigator.toml`
5. Restart or run `/vn reload`

## Permissions

| Permission | Purpose |
|-----------|---------|
| `velocitynavigator.use` | Use `/lobby` |
| `velocitynavigator.admin` | Admin commands (hidden from non-admins) |
| `velocitynavigator.bypasscooldown` | Bypass `/lobby` cooldown |

## FAQ

**Is this a Spigot/Paper plugin?**
No. VelocityNavigator runs exclusively on **Velocity proxies**. This Spigot listing is for visibility only.

**What Java version do I need?**
Java **17+**.

**What Velocity versions are supported?**
Velocity **3.x**.

**Does it support metrics?**
Yes — bStats plugin id `28341`. Can be disabled in config.

**Will it crash if I write the config wrong?**
No. Every field is validated individually — bad values get a warning log and are replaced with safe defaults. The proxy never crashes from a config typo.

---

## 📖 Documentation & Links

- **GitHub Repository**: [View Source](https://github.com/sdemonzdevelopment-spec/VelocityNavigator)
- **Configuration Guide**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Configuration-Guide)
- **Routing Algorithms**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Routing-Algorithms)
- **Initial Join Balancing**: [Read Here](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/wiki/Initial-Join-Balancing)

---

## 📊 Telemetry

[![bStats](https://bstats.org/signatures/velocity/Velocity%20Navigator.svg)](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341)

---

<p align="center">
  <img src="https://raw.githubusercontent.com/sdemonzdevelopment-spec/VelocityNavigator/main/assets/plugin-icon.png" alt="VelocityNavigator Icon" width="64">
  <br>
  <strong>Built with ❤️ by DemonZ Development</strong>
  <br>
  <em>Premium Minecraft infrastructure, engineered for scale.</em>
</p>
