package com.demonz.velocitynavigator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MessageFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of("server", "time", "reason", "mode");

    private MessageFormatter() {
    }

    public static Component render(String template) {
        return MINI_MESSAGE.deserialize(template == null ? "" : template);
    }

    public static Component render(String template, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return render(template);
        }
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (!ALLOWED_PLACEHOLDERS.contains(entry.getKey())) {
                continue;
            }
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
        }
        if (resolvers.isEmpty()) {
            return render(template);
        }
        return MINI_MESSAGE.deserialize(template == null ? "" : template, TagResolver.resolver(resolvers));
    }
}

