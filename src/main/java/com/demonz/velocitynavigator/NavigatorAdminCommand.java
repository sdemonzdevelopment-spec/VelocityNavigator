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
import java.util.Map;
import java.util.stream.Collectors;

public final class NavigatorAdminCommand implements SimpleCommand {

    private static final List<String> ROOT_SUBCOMMANDS = List.of("reload", "status", "version", "updatecheck", "debug", "drain", "undrain", "servers", "help");
    private static final List<String> DEBUG_TYPES = List.of("player", "server");
    private static final List<String> DRAIN_SUBCOMMANDS = List.of("status");

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
            case "updatecheck" -> updateCheck(invocation.source());
            case "debug" -> debug(invocation.source(), arguments);
            case "drain" -> drain(invocation.source(), arguments);
            case "undrain" -> undrain(invocation.source(), arguments);
            case "servers" -> ServersSubCommand.execute(invocation.source(), arguments, plugin);
            case "help" -> invocation.source().sendMessage(plugin.buildHelpComponent());
            default -> invocation.source().sendMessage(Component.text("Unknown subcommand. Use /velocitynavigator help.", NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("velocitynavigator.admin")) {
            return List.of();
        }

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

        if ("drain".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                List<String> options = new ArrayList<>(DRAIN_SUBCOMMANDS);
                options.addAll(plugin.server().getAllServers().stream()
                        .map(rs -> rs.getServerInfo().getName())
                        .toList());
                return options.stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        // FIX-3: Tab completion for top-level /vn undrain <server>
        if ("undrain".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return plugin.drainService().drainState().keySet().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
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

    private void updateCheck(CommandSource source) {
        source.sendMessage(MessageFormatter.render("<gray>Checking Modrinth for updates...</gray>"));
        plugin.updateChecker().checkAsync(plugin.config().updateChecker())
                .thenRun(() -> source.sendMessage(plugin.buildVersionComponent()))
                .exceptionally(throwable -> {
                    source.sendMessage(Component.text("Update check failed: " + throwable.getMessage(), NamedTextColor.RED));
                    return null;
                });
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
            plugin.previewRoute(player).thenAccept(decision -> source.sendMessage(plugin.buildPlayerDebugComponent(decision)))
                    .exceptionally(throwable -> {
                        source.sendMessage(Component.text("Debug route preview failed: " + throwable.getMessage(), NamedTextColor.RED));
                        return null;
                    });
            return;
        }

        if ("server".equals(targetType)) {
            plugin.inspectServer(targetName).thenAccept(status -> source.sendMessage(plugin.buildServerDebugComponent(status)))
                    .exceptionally(throwable -> {
                        source.sendMessage(Component.text("Debug server inspection failed: " + throwable.getMessage(), NamedTextColor.RED));
                        return null;
                    });
            return;
        }

        source.sendMessage(Component.text("Usage: /velocitynavigator debug player <name> | /velocitynavigator debug server <name>", NamedTextColor.YELLOW));
    }

    private void drain(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.sendMessage(Component.text("Usage: /vn drain <server> | /vn undrain <server> | /vn drain status", NamedTextColor.YELLOW));
            return;
        }

        String subCmd = arguments[1].toLowerCase();

        // Check if the argument matches a registered server name first.
        // This prevents server names like "status" or "undrain" from being
        // intercepted by the subcommand keywords.
        boolean isRegisteredServer = plugin.server().getAllServers().stream()
                .anyMatch(rs -> rs.getServerInfo().getName().equalsIgnoreCase(subCmd));

        if ("status".equals(subCmd) && !isRegisteredServer) {
            Map<String, Boolean> state = plugin.drainService().drainState();
            if (state.isEmpty()) {
                source.sendMessage(Component.text("No servers are currently drained.", NamedTextColor.GREEN));
            } else {
                source.sendMessage(MessageFormatter.render("<gradient:#8EF7FF:#D9F7FF><bold>Drained Servers</bold></gradient>"));
                for (Map.Entry<String, Boolean> entry : state.entrySet()) {
                    if (entry.getValue()) {
                        source.sendMessage(Component.text("  - " + entry.getKey(), NamedTextColor.RED));
                    }
                }
            }
            return;
        }

        if ("undrain".equals(subCmd) && !isRegisteredServer) {
            if (arguments.length < 3) {
                source.sendMessage(Component.text("Usage: /vn undrain <server>", NamedTextColor.YELLOW));
                return;
            }
            String serverName = arguments[2];
            plugin.drainService().undrain(serverName.toLowerCase(Locale.ROOT));
            source.sendMessage(Component.text("Server '" + serverName + "' is no longer drained.", NamedTextColor.GREEN));
            return;
        }

        // Default: drain the server (also handles servers named "status"/"undrain")
        String serverName = arguments[1];
        plugin.drainService().drain(serverName.toLowerCase(Locale.ROOT));
        source.sendMessage(Component.text("Server '" + serverName + "' is now drained. No players will be routed to it.", NamedTextColor.YELLOW));
    }

    /**
     * FIX-3: Top-level /vn undrain <server> command handler.
     */
    private void undrain(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.sendMessage(Component.text("Usage: /vn undrain <server>", NamedTextColor.YELLOW));
            return;
        }
        String serverName = arguments[1];
        plugin.drainService().undrain(serverName.toLowerCase(Locale.ROOT));
        source.sendMessage(Component.text("Server '" + serverName + "' is no longer drained.", NamedTextColor.GREEN));
    }
}
