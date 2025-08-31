# 🧭 VelocityNavigator


A lightweight and configurable lobby and server navigation plugin for Velocity-powered Minecraft networks. This is the very first public plugin from **DemonZ Development** 💫!

VelocityNavigator provides a simple, global command to send players to your hub or lobby servers, with the ability to configure multiple lobbies for load balancing.

## ✨ Features

- **Global Commands**: Use `/lobby`, `/hub`, or `/spawn` from any backend server to navigate.
- **Easy Default Mode**: Works out-of-the-box by connecting players to a server named `lobby`.
- **Powerful Manual Mode**: Configure a list of multiple lobby servers. The plugin will randomly select one to distribute player load.
- **Lightweight**: Designed to be simple and efficient, doing one job perfectly.
- **Fully Configurable**: All settings are controlled through a simple `navigator.toml` file.

## 🚀 Installation

1.  Download the latest `.jar` file from the [Releases](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/releases/) page.
2.  Place the `VelocityNavigator-x.x.x.jar` file into the `plugins` folder of your Velocity proxy.
3.  Start your proxy once to generate the default configuration file.
4.  Stop the proxy. Navigate to `plugins/velocitynavigator/` and edit the `navigator.toml` to your liking.
5.  Start your proxy again. You're all set!

## ⚙️ Configuration

The `navigator.toml` file is simple and powerful.

```toml
# Set to 'true' to enable manual mode and use the 'lobbyServers' list below.
# Set to 'false' to use the default mode (connects to a server named "lobby").
manualLobbySetup = false

# A list of your lobby server names (as defined in your main velocity.toml).
# These are only used when manualLobbySetup is set to true.
lobbyServers = [ "lobby1", "lobby2", "lobby3" ]
```

### Example: Manual Mode

To use two lobby servers named `hub-1` and `hub-2`, your config would look like this:

```toml
manualLobbySetup = true
lobbyServers = [ "hub-1", "hub-2" ]
```

## 💬 Commands

The plugin provides one main command with several aliases. By default, no permissions are required.

| Command | Aliases         | Description                            |
| :------ | :-------------- | :------------------------------------- |
| `/lobby`  | `/hub`, `/spawn` | Connects the player to a lobby server. |


## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

### ❤️ Credits

Developed with passion by **DemonZ Development**. This is our first of many plugins to come!
