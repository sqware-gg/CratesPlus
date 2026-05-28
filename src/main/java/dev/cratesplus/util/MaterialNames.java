package dev.cratesplus.util;

import java.util.Locale;
import org.bukkit.Material;

public final class MaterialNames {
    private MaterialNames() {
    }

    public static String display(Material material) {
        if (material == null) {
            return "Unknown";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
