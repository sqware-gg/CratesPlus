package dev.cratesplus.listener;

import dev.cratesplus.crate.CrateActionResult;
import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateService;
import dev.cratesplus.gui.CratesGui;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class CrateListener implements Listener {
    private final CrateService service;
    private final CratesGui gui;

    public CrateListener(CrateService service, CratesGui gui) {
        this.service = service;
        this.gui = gui;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        service.crateItemCrateId(item).ifPresent(crateId -> {
            CrateDefinition crate = service.crate(crateId);
            if (crate == null) {
                return;
            }
            if (!player.hasPermission("cratesplus.admin")) {
                event.setCancelled(true);
                service.send(player, "no-permission", Map.of());
                return;
            }
            service.send(player, service.setBlock(event.getBlockPlaced(), crate));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (service.crateAt(block).isEmpty()) {
            return;
        }
        if (!player.hasPermission("cratesplus.block.break")) {
            event.setCancelled(true);
            service.send(player, "block-protected", Map.of());
            return;
        }
        service.send(player, service.removeBlock(block));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            service.keyCrateId(event.getItem()).ifPresent(crateId -> open(player, crateId));
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        service.crateAt(block).ifPresent(crateId -> {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (!player.hasPermission("cratesplus.preview")) {
                    service.send(player, "no-permission", Map.of());
                    return;
                }
                gui.openPreview(player, crateId, 0);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                open(player, crateId);
            }
        });
    }

    private void open(Player player, String crateId) {
        if (!player.hasPermission("cratesplus.open")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        CrateActionResult result = service.open(player, crateId, 1);
        service.send(player, result);
        if (result.success()) {
            gui.openResult(player, result.crate(), result.rewards());
        }
    }
}
