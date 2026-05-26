package com.demonz.velocitynavigator;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyColorConverter {

    private static final Pattern LEGACY_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    private static final Map<Character, String> CONVERSIONS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private LegacyColorConverter() {
    }

    public static boolean hasLegacyCodes(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return LEGACY_PATTERN.matcher(input).find();
    }

    public static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = LEGACY_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String replacement = CONVERSIONS.get(code);
            if (replacement != null) {
                matcher.appendReplacement(sb, replacement);
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
