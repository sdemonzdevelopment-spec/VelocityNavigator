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

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class BedrockFormService {

    private BedrockFormService() {
    }

    public static void showLobbySelectionForm(Player player, VelocityNavigator plugin, RouteDecision decision) {
        Config config = plugin.config();
        List<String> candidates = decision.onlineCandidates();
        if (candidates == null || candidates.isEmpty()) {
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(), Map.of("reason", "No online lobby servers found.", "player", player.getUsername()), player));
            return;
        }

        try {
            Object builder = createSimpleFormBuilder(player, plugin, config, candidates);
            Class<?> formBuilderClass = Class.forName("org.geysermc.cumulus.form.util.FormBuilder");
            Class<?> responseClass = Class.forName("org.geysermc.cumulus.response.SimpleFormResponse");
            Method clickedButtonIdMethod = responseClass.getMethod("clickedButtonId");
            formBuilderClass.getMethod("validResultHandler", Consumer.class).invoke(builder, (Consumer<Object>) response -> {
                try {
                    Object clicked = clickedButtonIdMethod.invoke(response);
                    if (clicked instanceof Number clickedIndex && clickedIndex.intValue() >= 0 && clickedIndex.intValue() < candidates.size()) {
                        String targetServer = candidates.get(clickedIndex.intValue());
                        connectValidatedSelection(player, plugin, config, targetServer);
                    }
                } catch (ReflectiveOperationException exception) {
                    plugin.logger().warn("[VelocityNavigator] Could not read Bedrock form response for {}: {}",
                            player.getUsername(), exception.getMessage());
                }
            });

            sendFloodgateForm(player.getUniqueId(), builder, formBuilderClass);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            plugin.logger().warn("[VelocityNavigator] Bedrock form GUI is unavailable. Falling back to chat menu for {}: {}",
                    player.getUsername(), exception.getMessage());
            JavaMenuService.showLobbyMenu(player, plugin, decision);
        }
    }

    private static Object createSimpleFormBuilder(Player player, VelocityNavigator plugin, Config config, List<String> candidates)
            throws ReflectiveOperationException {
        Class<?> simpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
        Class<?> simpleFormBuilderClass = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder");
        Class<?> formBuilderClass = Class.forName("org.geysermc.cumulus.form.util.FormBuilder");

        Object builder = simpleFormClass.getMethod("builder").invoke(null);
        formBuilderClass.getMethod("title", String.class).invoke(builder,
                stripFormattingCodesIfRequested(config.bedrock().guiTitle(), config));
        simpleFormBuilderClass.getMethod("content", String.class).invoke(builder,
                stripFormattingCodesIfRequested(config.bedrock().guiContent(), config));

        for (String serverName : candidates) {
            int currentPlayers = 0;
            Optional<RegisteredServer> registered = plugin.server().getServer(serverName);
            if (registered.isPresent()) {
                currentPlayers = registered.get().getPlayersConnected().size();
            }
            String buttonText = config.bedrock().guiButtonFormat()
                    .replace("{server}", serverName)
                    .replace("{players}", String.valueOf(currentPlayers));

            simpleFormBuilderClass.getMethod("button", String.class).invoke(builder,
                    stripFormattingCodesIfRequested(buttonText, config));
        }
        return builder;
    }

    private static void sendFloodgateForm(UUID playerId, Object builder, Class<?> formBuilderClass)
            throws ReflectiveOperationException {
        Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
        Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
        if (floodgateApi == null) {
            throw new IllegalStateException("Floodgate API is not initialized");
        }
        floodgateApiClass.getMethod("sendForm", UUID.class, formBuilderClass).invoke(floodgateApi, playerId, builder);
    }

    private static void connectValidatedSelection(Player player, VelocityNavigator plugin, Config config, String targetServer) {
        plugin.previewRoute(player).thenAccept(decision -> {
            boolean stillAvailable = decision.onlineCandidates().stream()
                    .anyMatch(candidate -> candidate.equalsIgnoreCase(targetServer));
            if (!stillAvailable) {
                plugin.cooldowns().clear(player.getUniqueId());
                player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                        Map.of("reason", "The selected lobby is no longer available.", "player", player.getUsername()), player));
                return;
            }

            boolean sameServer = player.getCurrentServer()
                    .map(current -> current.getServerInfo().getName().equalsIgnoreCase(targetServer))
                    .orElse(false);
            if (sameServer && !config.commands().reconnectIfSameServer()) {
                plugin.cooldowns().clear(player.getUniqueId());
                player.sendMessage(MessageFormatter.render(config.messages().alreadyConnected(),
                        Map.of("server", targetServer, "player", player.getUsername()), player));
                return;
            }

            Optional<RegisteredServer> target = plugin.server().getServer(targetServer);
            if (target.isEmpty()) {
                plugin.cooldowns().clear(player.getUniqueId());
                player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                        Map.of("reason", "The selected server is no longer registered.", "player", player.getUsername()), player));
                return;
            }

            player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                    Map.of("server", targetServer, "player", player.getUsername()), player));

            RouteDecision formDecision = ConnectionWorkflow.withTargetFirst(decision, targetServer, "bedrock_gui");
            ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), formDecision, "bedrock_gui");
        }).exceptionally(throwable -> {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", "Could not validate the selected lobby.", "player", player.getUsername()), player));
            plugin.logger().error("[VelocityNavigator] Failed to validate Bedrock lobby selection for {}", player.getUsername(), throwable);
            return null;
        });
    }

    private static String stripFormattingCodesIfRequested(String text, Config config) {
        if (text == null) {
            return "";
        }
        if (config.bedrock().stripAdvancedFormatting()) {
            return text.replaceAll("<[^>]*>", "").replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
        }
        return text;
    }
}
