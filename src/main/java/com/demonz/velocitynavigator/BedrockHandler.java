package com.demonz.velocitynavigator;

import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

public final class BedrockHandler {

    private final ProxyServer proxy;

    public BedrockHandler(ProxyServer proxy) {
        this.proxy = proxy;
    }

    public boolean isBedrockSupported(Config config) {
        if (config == null || config.bedrock() == null) {
            return false;
        }
        if (config.bedrock().enabled()) {
            return true;
        }
        if (config.bedrock().autoDetect()) {
            return FloodgateIntegration.isAvailable() || hasGeyserOrFloodgatePlugin();
        }
        return false;
    }

    public boolean isBedrockPlayer(Player player, Config config) {
        if (!isBedrockSupported(config)) {
            return false;
        }
        return FloodgateIntegration.isBedrockPlayer(player);
    }

    private boolean hasGeyserOrFloodgatePlugin() {
        if (proxy == null) {
            return false;
        }
        PluginManager pm = proxy.getPluginManager();
        return pm.getPlugin("floodgate").isPresent() ||
               pm.getPlugin("geyser").isPresent() ||
               pm.getPlugin("geyser-velocity").isPresent();
    }

    public static Component formatForBedrock(Component component) {
        if (component == null) {
            return Component.empty();
        }
        Component formatted = component.hoverEvent(null)
                .clickEvent(null)
                .insertion(null);
        if (component.font() != null) {
            formatted = formatted.font(null);
        }
        return formatted.children(component.children().stream()
                .map(BedrockHandler::formatForBedrock)
                .toList());
    }
}
