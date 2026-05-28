package dev.cratesplus.crate;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public record CrateDefinition(
        String id,
        String displayName,
        List<String> description,
        Material icon,
        String keyName,
        String crateItemName,
        String permission,
        long cooldownMillis,
        double openCost,
        int previewRows,
        int openingRows,
        boolean allowMassOpen,
        CrateParticles particles,
        List<CrateReward> rewards,
        Map<Integer, CrateMilestone> milestones
) {
    public CrateDefinition {
        description = List.copyOf(description);
        rewards = List.copyOf(rewards);
        milestones = Map.copyOf(milestones);
    }

    public boolean hasPermission(Player player) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    public double totalWeight() {
        return rewards.stream().mapToDouble(CrateReward::weight).filter(weight -> weight > 0.0D).sum();
    }
}
