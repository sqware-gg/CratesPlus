package dev.cratesplus.gui;

import dev.cratesplus.crate.CrateActionResult;
import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateService;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class CratesMenuListener implements Listener {
    private final CrateService service;
    private final CratesGui gui;

    public CratesMenuListener(CrateService service, CratesGui gui) {
        this.service = service;
        this.gui = gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof CratesMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topInventory.getSize()) {
            return;
        }
        int slot = event.getRawSlot();
        switch (holder.type()) {
            case LIST -> handleList(player, holder, slot, event.isRightClick());
            case PREVIEW -> handlePreview(player, holder, slot);
            case RESULT -> handleResult(player, holder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CratesMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleList(Player player, CratesMenuHolder holder, int slot, boolean rightClick) {
        String crateId = holder.crateAt(slot);
        if (crateId != null) {
            if (rightClick) {
                openCrate(player, crateId, 1);
                return;
            }
            if (!player.hasPermission("cratesplus.preview")) {
                service.send(player, "no-permission", Map.of());
                return;
            }
            gui.openPreview(player, crateId, 0);
            return;
        }
        if (slot == 45 && holder.page() > 0) {
            gui.openList(player, holder.page() - 1);
        } else if (slot == 49) {
            gui.openList(player, holder.page());
        } else if (slot == 53) {
            gui.openList(player, holder.page() + 1);
        }
    }

    private void handlePreview(Player player, CratesMenuHolder holder, int slot) {
        int bottom = player.getOpenInventory().getTopInventory().getSize() - 9;
        if (slot == bottom && holder.page() > 0) {
            gui.openPreview(player, holder.crateId(), holder.page() - 1);
        } else if (slot == bottom + 3) {
            gui.openList(player, 0);
        } else if (slot == bottom + 4) {
            openCrate(player, holder.crateId(), 1);
        } else if (slot == bottom + 5) {
            openCrate(player, holder.crateId(), Integer.MAX_VALUE);
        } else if (slot == bottom + 8) {
            gui.openPreview(player, holder.crateId(), holder.page() + 1);
        }
    }

    private void handleResult(Player player, CratesMenuHolder holder, int slot) {
        int bottom = player.getOpenInventory().getTopInventory().getSize() - 9;
        if (slot == bottom + 3) {
            gui.openPreview(player, holder.crateId(), 0);
        } else if (slot == bottom + 4) {
            openCrate(player, holder.crateId(), 1);
        } else if (slot == bottom + 5) {
            gui.openList(player, 0);
        }
    }

    private void openCrate(Player player, String crateId, int amount) {
        if (!player.hasPermission("cratesplus.open")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        CrateActionResult result = service.open(player, crateId, amount);
        service.send(player, result);
        if (result.success()) {
            CrateDefinition crate = result.crate();
            gui.openResult(player, crate, result.rewards());
        }
    }
}
