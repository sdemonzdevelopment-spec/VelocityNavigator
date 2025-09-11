# 🧭 VelocityNavigator
## A public plugin from **DemonZ Development** 💫!
A lightweight and configurable lobby and server navigation plugin for Velocity-powered Minecraft networks. VelocityNavigator provides simple, global commands to send players to your hub or lobby servers, with multiple modes for balancing and behavior.

## ✨ Features
* **Global Commands**: A robust `/lobby` command to navigate from any backend server.
* **Highly Configurable**: Control everything from command aliases to connection logic through the simple `navigator.toml` file.
* **Permission Support**: An optional permission node to control who can use the commands.
* **Smart Reconnect**: Intelligently handles cases where players are already in a lobby, allowing them to be sent to spawn instead of just seeing an error.
* **Automatic Updates**: Seamlessly migrates old configuration files to the newest format on plugin updates, preserving your settings.
* **Lightweight & Efficient**: Designed to do one job perfectly without adding bulk to your proxy.

## 🚀 Installation
1. Download the latest `.jar` file from the project's Releases page.
2. Place the `VelocityNavigator-x.x.x.jar` file into the `plugins` folder of your Velocity proxy.
3. Start your proxy once to generate the default configuration file.
4. Stop the proxy. Navigate to `plugins/velocitynavigator/` and edit the `navigator.toml` to your liking.
5. Start your proxy again. You're all set!

## ⚙️ Configuration
The `navigator.toml` file is simple and powerful. Here is the default configuration with explanations for each option.
```toml
# A DemonZDevelopment Project
# VelocityNavigator

[commands]
# The permission node required to use the lobby commands. Set to "" to allow everyone.
permission = "velocitynavigator.use"
# A list of command aliases to send players to the lobby. "/lobby" is the base command.
aliases = [ "hub", "spawn" ]

[settings]
# If true, connects to a random server from 'lobbyServers'. If false, connects to "lobby".
manualLobbySetup = false
# If true, using /lobby in a lobby will reconnect you (effectively sending you to spawn).
reconnectOnLobbyCommand = true
# A list of your lobby server names (only used when manualLobbySetup is true).
lobbyServers = [ "lobby1", "lobby2", "lobby3" ]
```
## 💬 Commands
* **Command**: `/lobby`
* **Default Aliases**: `/hub`, `/spawn`
* **Default Permission**: `velocitynavigator.use`
* **Description**: Connects the player to a lobby server.

## 📜 License
This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

### ❤️ Credits
Developed with passion by **DemonZ Development**.

