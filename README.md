# VelocityNavigator

![Version](https://img.shields.io/badge/Version-v2.0.0--Stable-brightgreen)
![Velocity](https://img.shields.io/badge/Velocity-3.3.0%2B-blue)
![Java](https://img.shields.io/badge/Java-17%2B-orange)

**VelocityNavigator** is an enterprise-grade, intelligent lobby navigation system built for Velocity proxies.  
Developed by **DemonZ Development**.

---

## ✨ Features

- 🔄 **Modrinth Integration**  
  Automatically checks the Modrinth API for updates during startup.

- 🧭 **Intelligent Load Balancing**  
  Supports three routing strategies:  
  • *Least Players*  
  • *Random*  
  • *Round Robin*

- 🗺️ **Contextual Lobbies**  
  Smart origin-based routing (e.g., players from `bedwars-1` → `bedwars-lobby`).

- 🛡️ **Smart Failover**  
  Servers that fail to respond within **2.5 seconds** are treated as offline and removed from rotation.

- 🧩 **Crash-Proof Config**  
  Validates all config types to prevent crashes caused by incorrect values.

- 🔁 **Lobby Cycling**  
  Ensures players connecting to lobby commands are always routed to a *different* lobby when possible.

- 📢 **User Feedback**  
  Provides explicit error messages such as `"Server Full"` or `"Lobby Unavailable"` rather than silent failures.

---

## ⚙️ Configuration

Below is the exact configuration format used by VelocityNavigator:

```toml
[commands]
aliases = ["hub", "spawn", "lobby"]

[settings]
lobby_servers = ["lobby-1", "lobby-2"]
selection_mode = "LEAST_PLAYERS" 

# Network Resiliency
ping_before_connect = true
ping_cache_duration = 60

[advanced_settings]
use_contextual_lobbies = false
```

---

## 📚 Commands

| Command | Aliases | Permission | Description |
|--------|---------|------------|-------------|
| `/lobby` | `/hub`, `/spawn` | `velocitynavigator.use` | Sends the player to an available lobby following the selection rules. |

---

## 🚀 Getting Started

1. Place the plugin JAR inside your Velocity `plugins/` directory.  
2. Start the proxy once to generate the configuration file.  
3. Adjust the TOML config to suit your network layout.  
4. Reload or restart the proxy to apply changes.

---

## 📦 Directory Structure

```
plugins/
 └── VelocityNavigator.jar
```

---

## 📝 Notes

- VelocityNavigator v2.0.0-Stable is fully compatible with **Velocity 3.3.0+** and **Java 17+**.
- Designed for production networks requiring reliability, clarity, and predictable routing behavior.

---

## © DemonZ Development
Professional proxy tooling for modern Minecraft networks.
