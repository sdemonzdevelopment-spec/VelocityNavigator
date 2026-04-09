package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class NavigatorAdminCommand implements SimpleCommand {

    private static final List<String> ROOT_SUBCOMMANDS = List.of("reload", "status", "version", "debug", "help");
    private static final List<String> DEBUG_TYPES = List.of("player", "server");

    private final VelocityNavigator plugin;

    public NavigatorAdminCommand(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("velocitynavigator.admin")) {
            invocation.source().sendMessage(Component.text("You do not have permission to use VelocityNavigator admin commands.", NamedTextColor.RED));
            return;
        }

        String[] arguments = invocation.arguments();
        if (arguments.length == 0) {
            invocation.source().sendMessage(plugin.buildHelpComponent());
            return;
        }

        switch (arguments[0].toLowerCase()) {
            case "reload" -> reload(invocation.source());
            case "status" -> invocation.source().sendMessage(plugin.buildStatusComponent());
            case "version" -> invocation.source().sendMessage(plugin.buildVersionComponent());
            case "debug" -> debug(invocation.source(), arguments);
            case "help" -> invocation.source().sendMessage(plugin.buildHelpComponent());
            default -> invocation.source().sendMessage(Component.text("Unknown subcommand. Use /velocitynavigator help.", NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return ROOT_SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if ("debug".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return DEBUG_TYPES.stream()
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                String type = args[1].toLowerCase(Locale.ROOT);
                String partial = args[2].toLowerCase(Locale.ROOT);
                if ("player".equals(type)) {
                    return plugin.server().getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                            .collect(Collectors.toList());
                }
                if ("server".equals(type)) {
                    return plugin.server().getAllServers().stream()
                            .map(rs -> rs.getServerInfo().getName())
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                            .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocitynavigator.admin");
    }

    private void reload(CommandSource source) {
        try {
            plugin.reloadConfiguration();
            source.sendMessage(MessageFormatter.render(plugin.config().messages().reloadSuccess()));
        } catch (IOException exception) {
            source.sendMessage(MessageFormatter.render(plugin.config().messages().reloadFailed()));
            plugin.logger().error("VelocityNavigator reload failed.", exception);
        }
    }

    private void debug(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.sendMessage(Component.text("Usage: /velocitynavigator debug player <name> | /velocitynavigator debug server <name>", NamedTextColor.YELLOW));
            return;
        }

        String targetType = arguments[1].toLowerCase();
        String targetName = arguments[2];
        if ("player".equals(targetType)) {
            Player player = plugin.server().getPlayer(targetName).orElse(null);
            if (player == null) {
                source.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
                return;
            }
            plugin.previewRoute(player).thenAccept(decision -> source.sendMessage(plugin.buildPlayerDebugComponent(decision)));
            return;
        }

        if ("server".equals(targetType)) {
            plugin.inspectServer(targetName).thenAccept(status -> source.sendMessage(plugin.buildServerDebugComponent(status)));
            return;
        }

        source.sendMessage(Component.text("Usage: /velocitynavigator debug player <name> | /velocitynavigator debug server <name>", NamedTextColor.YELLOW));
    }
}
