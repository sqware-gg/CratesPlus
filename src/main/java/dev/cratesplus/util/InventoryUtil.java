package dev.cratesplus.util;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static int give(Player player, ItemStack item, boolean dropOverflow) {
        if (isEmpty(item)) {
            return 0;
        }
        int leftoverAmount = 0;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        for (ItemStack leftover : leftovers.values()) {
            leftoverAmount += leftover.getAmount();
            if (dropOverflow) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        return leftoverAmount;
    }

    public static int spaceFor(Inventory inventory, ItemStack item) {
        if (isEmpty(item)) {
            return Integer.MAX_VALUE;
        }
        int space = 0;
        int maxStack = item.getMaxStackSize();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (isEmpty(slot)) {
                space += maxStack;
            } else if (slot.isSimilar(item)) {
                space += Math.max(0, maxStack - slot.getAmount());
            }
        }
        return space;
    }
}
