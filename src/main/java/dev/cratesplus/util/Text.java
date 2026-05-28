package dev.cratesplus.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {
    private static final Pattern HEX_COLOR = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final char COLOR_CHAR = '\u00A7';
    private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private Text() {
    }

    public static String color(String text) {
        return translateLegacyCodes(replaceHex(text == null ? "" : text));
    }

    public static String render(String template, Map<String, String> placeholders) {
        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    public static Component component(String text) {
        return LEGACY.deserialize(color(text));
    }

    private static String replaceHex(String text) {
        Matcher matcher = HEX_COLOR.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder(String.valueOf(COLOR_CHAR)).append('x');
            for (char character : hex.toCharArray()) {
                replacement.append(COLOR_CHAR).append(character);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String translateLegacyCodes(String text) {
        char[] characters = text.toCharArray();
        for (int index = 0; index < characters.length - 1; index++) {
            if (characters[index] == '&' && LEGACY_CODES.indexOf(characters[index + 1]) >= 0) {
                characters[index] = COLOR_CHAR;
                characters[index + 1] = Character.toLowerCase(characters[index + 1]);
            }
        }
        return new String(characters);
    }
}
