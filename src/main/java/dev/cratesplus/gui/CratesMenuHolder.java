package dev.cratesplus.gui;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CratesMenuHolder implements InventoryHolder {
    private final MenuType type;
    private final int page;
    private final String crateId;
    private final Map<Integer, String> crates = new HashMap<>();
    private Inventory inventory;

    public CratesMenuHolder(MenuType type, int page, String crateId) {
        this.type = type;
        this.page = page;
        this.crateId = crateId;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public MenuType type() {
        return type;
    }

    public int page() {
        return page;
    }

    public String crateId() {
        return crateId;
    }

    public void mapCrate(int slot, String id) {
        crates.put(slot, id);
    }

    public String crateAt(int slot) {
        return crates.get(slot);
    }
}
