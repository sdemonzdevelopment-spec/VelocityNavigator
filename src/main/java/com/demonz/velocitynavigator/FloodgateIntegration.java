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
import java.util.UUID;

public final class FloodgateIntegration {

    private static boolean available;

    static {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
    }

    private FloodgateIntegration() {
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isBedrockPlayer(Player player) {
        if (!available || player == null) {
            return false;
        }
        try {
            return FloodgateReflector.isBedrock(player.getUniqueId());
        } catch (LinkageError | Exception e) {
            return false;
        }
    }

    public static UUID getJavaUUID(Player player) {
        if (player == null) {
            return null;
        }
        if (!available) {
            return player.getUniqueId();
        }
        try {
            UUID correct = FloodgateReflector.getJavaUUID(player.getUniqueId());
            return correct != null ? correct : player.getUniqueId();
        } catch (LinkageError | Exception e) {
            return player.getUniqueId();
        }
    }

    private static class FloodgateReflector {
        static boolean isBedrock(UUID uuid) throws ReflectiveOperationException {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
            return Boolean.TRUE.equals(result);
        }

        static UUID getJavaUUID(UUID uuid) throws ReflectiveOperationException {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object floodgatePlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, uuid);
            if (floodgatePlayer == null) {
                return null;
            }
            Class<?> playerClass = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer");
            Object correctUuid = playerClass.getMethod("getCorrectUniqueId").invoke(floodgatePlayer);
            return correctUuid instanceof UUID uuidResult ? uuidResult : null;
        }
    }
}
