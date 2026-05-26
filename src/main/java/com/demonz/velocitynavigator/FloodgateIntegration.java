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
        static boolean isBedrock(UUID uuid) {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        }

        static UUID getJavaUUID(UUID uuid) {
            org.geysermc.floodgate.api.player.FloodgatePlayer fp =
                    org.geysermc.floodgate.api.FloodgateApi.getInstance().getPlayer(uuid);
            return fp != null ? fp.getCorrectUniqueId() : null;
        }
    }
}
