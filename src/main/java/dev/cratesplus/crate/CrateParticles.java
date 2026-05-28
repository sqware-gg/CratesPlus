package dev.cratesplus.crate;

import org.bukkit.Particle;

public record CrateParticles(
        boolean enabled,
        Particle particle,
        int count,
        double offsetX,
        double offsetY,
        double offsetZ,
        double speed
) {
}
