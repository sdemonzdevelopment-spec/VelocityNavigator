package com.example.velocitynavigator;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class LobbyCommand implements SimpleCommand {

    private final ProxyServer server;
    private final Config config;
    private final Random random = new Random();

    public LobbyCommand(ProxyServer server, Config config) {
        this.server = server;
        this.config = config;
    }

    @Override
    public void execute(final Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text("This command can only be run by a player."));
            return;
        }

        Player player = (Player) invocation.source();
        Optional<RegisteredServer> targetServer;
        String targetServerName;

        if (config.isManualLobbySetup()) {
            // MANUAL MODE: Pick a random lobby from the configured list
            List<String> lobbies = config.getLobbyServers();
            if (lobbies == null || lobbies.isEmpty()) {
                player.sendMessage(Component.text("Error: Manual mode is enabled, but no lobby servers are configured!", NamedTextColor.RED));
                return;
            }
            targetServerName = lobbies.get(random.nextInt(lobbies.size()));
            targetServer = server.getServer(targetServerName);

        } else {
            // DEFAULT MODE: Connect to the server named "lobby"
            targetServerName = "lobby";
            targetServer = server.getServer(targetServerName);
        }

        // Process the connection request
        if (!targetServer.isPresent()) {
            player.sendMessage(Component.text("The lobby server '" + targetServerName + "' could not be found.", NamedTextColor.RED));
            return;
        }
        
        if (player.getCurrentServer().map(cs -> cs.getServer().equals(targetServer.get())).orElse(false)) {
            player.sendMessage(Component.text("You are already connected to this lobby!", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("Connecting you to the lobby...", NamedTextColor.GREEN));
        player.createConnectionRequest(targetServer.get()).fireAndForget();
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        // Everyone can use this command by default.
        return true;
    }
}
