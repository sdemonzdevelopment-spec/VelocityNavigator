package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyCommand implements SimpleCommand {

    private final ProxyServer server;
    private final Config config;
    private final Random random = new Random();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Instant> cooldowns = new ConcurrentHashMap<>();

    public LobbyCommand(ProxyServer server, Config config) {
        this.server = server;
        this.config = config;
    }

    @Override
    public void execute(final Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(mm.deserialize(config.getMessages().getPlayerOnly()));
            return;
        }

        Player player = (Player) invocation.source();
        
        if (checkCooldown(player)) return;
        if (checkBlacklist(player)) return;

        Optional<RegisteredServer> targetServer = getTargetLobby(player);

        if (targetServer.isEmpty()) {
            player.sendMessage(mm.deserialize(config.getMessages().getNoLobbyFound()));
            return;
        }
        
        boolean isAlreadyConnected = player.getCurrentServer()
                .map(cs -> cs.getServer().equals(targetServer.get()))
                .orElse(false);

        if (isAlreadyConnected && !config.isReconnectOnLobbyCommand()) {
            player.sendMessage(mm.deserialize(config.getMessages().getAlreadyConnected()));
            return;
        }

        player.sendMessage(mm.deserialize(config.getMessages().getConnecting()));
        player.createConnectionRequest(targetServer.get()).fireAndForget();
        setCooldown(player);
    }

    private Optional<RegisteredServer> getTargetLobby(Player player) {
        List<String> lobbyNames = getLobbyGroupForPlayer(player);
        if (lobbyNames == null || lobbyNames.isEmpty()) {
            return Optional.empty();
        }

        String mode = config.getLobbySelectionMode();

        if ("LEAST_PLAYERS".equalsIgnoreCase(mode)) {
            return lobbyNames.stream()
                    .map(server::getServer)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .min(Comparator.comparingInt(rs -> rs.getPlayersConnected().size()));
        } else {
            String targetServerName = lobbyNames.get(random.nextInt(lobbyNames.size()));
            return server.getServer(targetServerName);
        }
    }

    private List<String> getLobbyGroupForPlayer(Player player) {
        return player.getCurrentServer()
                .map(current -> current.getServerInfo().getName())
                .map(serverName -> config.getServerGroupMappings().getOrDefault(serverName, "default"))
                .map(groupName -> config.getServerGroups().get(groupName))
                .orElse(config.getServerGroups().get("default"));
    }
    
    private boolean checkBlacklist(Player player) {
        boolean isBlacklisted = player.getCurrentServer()
                .map(s -> config.getBlacklistFromServers().contains(s.getServerInfo().getName()))
                .orElse(false);

        if (isBlacklisted) {
            player.sendMessage(mm.deserialize(config.getMessages().getCommandDisabled()));
        }
        return isBlacklisted;
    }

    private boolean checkCooldown(Player player) {
        Instant cooldownEnd = cooldowns.get(player.getUniqueId());
        if (cooldownEnd != null && Instant.now().isBefore(cooldownEnd)) {
            long secondsLeft = Duration.between(Instant.now(), cooldownEnd).toSeconds() + 1;
            player.sendMessage(mm.deserialize(
                config.getMessages().getCooldown(),
                Placeholder.unparsed("time", String.valueOf(secondsLeft))
            ));
            return true;
        }
        return false;
    }

    private void setCooldown(Player player) {
        if (config.getCommandCooldown() > 0) {
            cooldowns.put(player.getUniqueId(), Instant.now().plusSeconds(config.getCommandCooldown()));
        }
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        boolean hasPerm = invocation.source().hasPermission(config.getCommandPermission());
        if (!hasPerm && invocation.source() instanceof Player) {
            invocation.source().sendMessage(mm.deserialize(config.getMessages().getNoPermission()));
        }
        return hasPerm;
    }
}