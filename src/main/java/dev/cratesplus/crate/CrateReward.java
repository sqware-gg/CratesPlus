package dev.cratesplus.crate;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record CrateReward(
        String id,
        String displayName,
        ItemStack item,
        boolean giveItem,
        double weight,
        String rarity,
        List<String> commands,
        boolean broadcast,
        RewardRequirements requirements
) {
    public CrateReward {
        item = item == null ? new ItemStack(Material.CHEST) : item.clone();
        commands = List.copyOf(commands);
        requirements = requirements == null
                ? new RewardRequirements(List.of(), List.of(), 0, 0, false, 0, 0, 0, 0L, 0, 0L)
                : requirements;
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }

    public Material material() {
        return item.getType();
    }

    public int amount() {
        return item.getAmount();
    }
}
