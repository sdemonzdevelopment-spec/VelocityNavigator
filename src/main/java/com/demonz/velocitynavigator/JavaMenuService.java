/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class JavaMenuService {

    private JavaMenuService() {
    }

    public static void showLobbyMenu(Player player, VelocityNavigator plugin, RouteDecision decision) {
        Config config = plugin.config();
        List<String> candidates = decision.onlineCandidates();
        if (candidates == null || candidates.isEmpty()) {
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(), Map.of("reason", "No online lobby servers found.", "player", player.getUsername()), player));
            return;
        }

        // Send header
        player.sendMessage(MessageFormatter.render(config.routing().chatMenuHeader()));
        String token = plugin.createMenuToken(player, candidates);

        for (String serverName : candidates) {
            String lobbyLower = serverName.toLowerCase(Locale.ROOT);
            boolean isDrained = plugin.drainService().isDrained(lobbyLower);

            CircuitBreaker.State cbStateEnum = CircuitBreaker.State.CLOSED;
            if (plugin.circuitBreaker() != null) {
                cbStateEnum = plugin.circuitBreaker().getState(lobbyLower);
            }
            boolean isCbOpen = cbStateEnum == CircuitBreaker.State.OPEN;

            String statusText;
            String colorTag;
            if (isCbOpen) {
                statusText = "CB_OPEN";
                colorTag = config.messages().dashboardOpen();
            } else if (isDrained) {
                statusText = "DRAINED";
                colorTag = config.messages().dashboardDraining();
            } else {
                statusText = "HEALTHY";
                colorTag = config.messages().dashboardHealthy();
            }

            int currentPlayers = 0;
            Optional<RegisteredServer> registered = plugin.server().getServer(serverName);
            if (registered.isPresent()) {
                currentPlayers = registered.get().getPlayersConnected().size();
            }

            int maxConfig = -1;
            boolean found = false;
            if (config.routing().defaultLobbies() != null) {
                for (Config.LobbyEntry entry : config.routing().defaultLobbies()) {
                    if (entry.server().equalsIgnoreCase(serverName)) {
                        maxConfig = entry.maxPlayers();
                        found = true;
                        break;
                    }
                }
            }
            if (!found && config.routing().contextual() != null && config.routing().contextual().groups() != null) {
                for (Config.GroupConfig groupConfig : config.routing().contextual().groups().values()) {
                    if (groupConfig.servers() == null) continue;
                    for (Config.LobbyEntry entry : groupConfig.servers()) {
                        if (entry.server().equalsIgnoreCase(serverName)) {
                            maxConfig = entry.maxPlayers();
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
            }
            String maxPlayersText = maxConfig == -1 ? "-" : String.valueOf(maxConfig);

            long latencyVal = -1L;
            if (plugin.healthService() != null) {
                Long l = plugin.healthService().getLatencies().get(lobbyLower);
                if (l != null) {
                    latencyVal = l;
                }
            }
            String latencyText = latencyVal < 0 ? "?" : String.valueOf(latencyVal);

            // Format line text
            String lineText = config.routing().chatMenuFormat()
                    .replace("{server}", serverName)
                    .replace("{players}", String.valueOf(currentPlayers))
                    .replace("{max_players}", maxPlayersText)
                    .replace("{status}", statusText)
                    .replace("{status_color}", colorTag)
                    .replace("{ping}", latencyText);

            // Format tooltip text
            String tooltipText = config.routing().chatMenuTooltip()
                    .replace("{server}", serverName)
                    .replace("{players}", String.valueOf(currentPlayers))
                    .replace("{max_players}", maxPlayersText)
                    .replace("{status}", statusText)
                    .replace("{status_color}", colorTag)
                    .replace("{ping}", latencyText);

            Component lineComponent = MessageFormatter.render(lineText, player)
                    .clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player clickedPlayer
                                && clickedPlayer.getUniqueId().equals(player.getUniqueId())) {
                            plugin.server().getCommandManager().executeAsync(clickedPlayer,
                                    config.commands().primary() + " connect " + serverName + " " + token);
                        }
                    }))
                    .hoverEvent(HoverEvent.showText(MessageFormatter.render(tooltipText, player)));

            player.sendMessage(lineComponent);
        }
    }
}
