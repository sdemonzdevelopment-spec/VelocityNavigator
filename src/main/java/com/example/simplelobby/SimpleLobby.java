package com.example.simplelobby;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public final class SimpleLobby extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        // Register the outgoing plugin message channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Register the /lobby command
        this.getCommand("lobby").setExecutor(this);

        getLogger().info("SimpleLobby has been enabled!");
    }

    @Override
    public void onDisable() {
        // Unregister the channel
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getLogger().info("SimpleLobby has been disabled!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Ensure the command sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        // Send the player to the "lobby" server
        // IMPORTANT: Change "lobby" to the exact name of your lobby server in velocity.toml
        connectPlayerToServer(player, "lobby");

        return true;
    }

    /**
     * Sends a plugin message to Velocity to connect a player to a specific server.
     *
     * @param player The player to send.
     * @param serverName The name of the server to connect to.
     */
    private void connectPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);

            // Send the plugin message to the BungeeCord channel
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
            player.sendMessage("§aConnecting you to the lobby...");

        } catch (Exception e) {
            player.sendMessage("§cError: Could not connect you to the lobby server.");
            getLogger().warning("Failed to send player " + player.getName() + " to server " + serverName);
            e.printStackTrace();
        }
    }
}
