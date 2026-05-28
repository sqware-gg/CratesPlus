package dev.cratesplus.gui;

import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateReward;
import dev.cratesplus.crate.CrateService;
import dev.cratesplus.util.DurationFormatter;
import dev.cratesplus.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CratesGui {
    private static final int LIST_SIZE = 54;
    private static final int LIST_PAGE_SIZE = 45;
    private static final Material RESULT_FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material RESULT_HIGHLIGHT = Material.LIME_STAINED_GLASS_PANE;

    private final CrateService service;

    public CratesGui(CrateService service) {
        this.service = service;
    }

    public void openList(Player player, int requestedPage) {
        List<CrateDefinition> crates = service.crates().stream()
                .sorted(Comparator.comparing(CrateDefinition::id))
                .toList();
        int pages = Math.max(1, (int) Math.ceil(crates.size() / (double) LIST_PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        CratesMenuHolder holder = new CratesMenuHolder(MenuType.LIST, page, null);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE,
                Text.component(service.config().guiTitle("title-list")));
        holder.attach(inventory);

        int start = page * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, crates.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            CrateDefinition crate = crates.get(index);
            inventory.setItem(slot, crateItem(player, crate));
            holder.mapCrate(slot, crate.id());
        }
        if (crates.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, "&#ED4245No crates", List.of("&7Add crates in config.yml.")));
        }

        inventory.setItem(45, icon(Material.ARROW, "&#2b98fdPrevious Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        inventory.setItem(49, icon(Material.SUNFLOWER, "&#2b98fdRefresh", List.of("&7Reload this page.")));
        inventory.setItem(53, icon(Material.ARROW, "&#2b98fdNext Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        player.openInventory(inventory);
    }

    public void openPreview(Player player, String crateId, int requestedPage) {
        CrateDefinition crate = service.crate(crateId);
        if (crate == null) {
            service.send(player, "invalid-crate", Map.of());
            return;
        }
        int rows = Math.max(3, crate.previewRows());
        int size = rows * 9;
        int rewardPageSize = size - 9;
        List<CrateReward> rewards = crate.rewards();
        int pages = Math.max(1, (int) Math.ceil(rewards.size() / (double) rewardPageSize));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        CratesMenuHolder holder = new CratesMenuHolder(MenuType.PREVIEW, page, crate.id());
        Inventory inventory = Bukkit.createInventory(holder, size,
                Text.component(service.config().guiTitle("title-preview")));
        holder.attach(inventory);

        int start = page * rewardPageSize;
        int end = Math.min(start + rewardPageSize, rewards.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, rewardItem(crate, rewards.get(index)));
        }
        fillPreviewControls(player, inventory, crate, page, pages);
        player.openInventory(inventory);
    }

    public void openResult(Player player, CrateDefinition crate, List<CrateReward> rewards) {
        int rows = Math.max(3, crate.openingRows());
        int size = rows * 9;
        CratesMenuHolder holder = new CratesMenuHolder(MenuType.RESULT, 0, crate.id());
        Inventory inventory = Bukkit.createInventory(holder, size,
                Text.component(service.config().guiTitle("title-opening")));
        holder.attach(inventory);

        List<ResultLine> results = resultLines(rewards);
        fillResultFrame(inventory);
        List<Integer> slots = centeredResultSlots(rows, results.size());
        for (int index = 0; index < Math.min(results.size(), slots.size()); index++) {
            ResultLine line = results.get(index);
            int slot = slots.get(index);
            setResultHighlight(inventory, slot);
            inventory.setItem(slot, resultItem(line.reward(), line.count()));
        }
        int bottom = size - 9;
        inventory.setItem(bottom + 3, icon(Material.BOOK, "&#2b98fdPreview", List.of("&7View all possible rewards.")));
        inventory.setItem(bottom + 4, icon(Material.LIME_CONCRETE, "&#57F287Open Again", List.of("&7Use another key.")));
        inventory.setItem(bottom + 5, icon(Material.CHEST, "&#2b98fdCrates", List.of("&7Return to the crate browser.")));
        player.openInventory(inventory);
    }

    private void fillPreviewControls(Player player, Inventory inventory, CrateDefinition crate, int page, int pages) {
        int bottom = inventory.getSize() - 9;
        inventory.setItem(bottom, icon(Material.ARROW, "&#2b98fdPrevious Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
        inventory.setItem(bottom + 2, keyBalanceItem(player, crate));
        inventory.setItem(bottom + 3, icon(Material.CHEST, "&#2b98fdCrates", List.of("&7Return to the crate browser.")));
        inventory.setItem(bottom + 4, icon(Material.LIME_CONCRETE, "&#57F287Open One", List.of("&7Use one key.")));
        inventory.setItem(bottom + 5, icon(Material.EMERALD_BLOCK, "&#57F287Open All", List.of("&7Use up to &f" + service.config().maxMassOpen() + " &7keys.")));
        inventory.setItem(bottom + 8, icon(Material.ARROW, "&#2b98fdNext Page", List.of("&7Page &f" + (page + 1) + "&8/&f" + pages)));
    }

    private ItemStack crateItem(Player player, CrateDefinition crate) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("crate", crate.displayName());
        placeholders.put("crate_id", crate.id());
        placeholders.put("virtual_keys", Integer.toString(service.virtualKeys(player.getUniqueId(), crate.id())));
        placeholders.put("physical_keys", Integer.toString(service.physicalKeys(player, crate.id())));
        placeholders.put("open_cost", crate.openCost() > 0.0D ? service.formatMoney(crate.openCost()) : "");
        placeholders.put("cooldown", crate.cooldownMillis() > 0L ? DurationFormatter.compact(crate.cooldownMillis()) : "");
        placeholders.put("permission", crate.permission() == null ? "" : crate.permission());
        placeholders.put("max_mass_open", Integer.toString(service.config().maxMassOpen()));
        List<String> lore = renderGuiLore(player, "list-crate-lore", placeholders, Map.of(
                "[description]", crate.description()
        ));
        return icon(crate.icon(), crate.displayName(), lore);
    }

    private ItemStack keyBalanceItem(Player player, CrateDefinition crate) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("crate", crate.displayName());
        placeholders.put("crate_id", crate.id());
        placeholders.put("virtual_keys", Integer.toString(service.virtualKeys(player.getUniqueId(), crate.id())));
        placeholders.put("physical_keys", Integer.toString(service.physicalKeys(player, crate.id())));
        placeholders.put("total_keys", Integer.toString(service.availableKeys(player, crate.id())));
        return icon(Material.TRIPWIRE_HOOK, "&#2b98fdYour Keys",
                renderGuiLore(player, "key-balance-lore", placeholders, Map.of()));
    }

    private ItemStack rewardItem(CrateDefinition crate, CrateReward reward) {
        double chance = crate.totalWeight() <= 0.0D ? 0.0D : (reward.weight() / crate.totalWeight()) * 100.0D;
        RewardRequirementsView requirements = requirementsView(reward);
        Map<String, String> placeholders = rewardPlaceholders(crate, reward, chance, 1);
        placeholders.put("requirements", requirements.lines().isEmpty() ? "" : "true");
        List<String> lore = renderGuiLore(null, "reward-lore", placeholders, Map.of(
                "[requirements]", requirements.lines()
        ));
        return rewardIcon(reward, lore, Math.min(reward.amount(), reward.material().getMaxStackSize()));
    }

    private ItemStack resultItem(CrateReward reward, int count) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("reward", reward.displayName());
        placeholders.put("reward_id", reward.id());
        placeholders.put("rarity", reward.rarity());
        placeholders.put("count", Integer.toString(count));
        placeholders.put("plural", count == 1 ? "" : "s");
        placeholders.put("item", reward.giveItem() ? reward.amount() + "x " + materialName(reward.material()) : "");
        List<String> lore = renderGuiLore(null, "result-lore", placeholders, Map.of());
        return rewardIcon(reward, lore, Math.min(count, reward.material().getMaxStackSize()));
    }

    private List<ResultLine> resultLines(List<CrateReward> rewards) {
        Map<String, ResultLine> lines = new LinkedHashMap<>();
        for (CrateReward reward : rewards) {
            lines.compute(reward.id(), (ignored, existing) ->
                    existing == null ? new ResultLine(reward, 1) : new ResultLine(reward, existing.count() + 1));
        }
        return new ArrayList<>(lines.values());
    }

    private void fillResultFrame(Inventory inventory) {
        ItemStack filler = icon(RESULT_FILLER, " ", List.of());
        int bottom = inventory.getSize() - 9;
        for (int slot = 0; slot < bottom; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private List<Integer> centeredResultSlots(int rows, int resultCount) {
        int contentRows = Math.max(2, rows - 1);
        int count = Math.max(1, Math.min(resultCount, contentRows * 7));
        List<Integer> slots = new ArrayList<>();
        int remaining = count;
        int startRow = contentRows == 2 && count <= 7 ? 1 : 0;
        for (int row = startRow; row < contentRows && remaining > 0; row++) {
            int rowsLeft = contentRows - row;
            int rowCount = Math.min(7, (int) Math.ceil(remaining / (double) rowsLeft));
            int startColumn = 4 - (rowCount / 2);
            if (rowCount % 2 == 0) {
                startColumn++;
            }
            for (int index = 0; index < rowCount; index++) {
                slots.add(row * 9 + startColumn + index);
            }
            remaining -= rowCount;
        }
        return slots;
    }

    private void setResultHighlight(Inventory inventory, int slot) {
        ItemStack highlight = icon(RESULT_HIGHLIGHT, " ", List.of());
        for (int neighbor : List.of(slot - 9, slot - 1, slot + 1, slot + 9)) {
            if (neighbor < 0 || neighbor >= inventory.getSize() - 9) {
                continue;
            }
            if (Math.abs((neighbor % 9) - (slot % 9)) > 1) {
                continue;
            }
            inventory.setItem(neighbor, highlight);
        }
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        return icon(material, name, lore, 1);
    }

    private ItemStack icon(Material material, String name, List<String> lore, int amount) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.CHEST : material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.component(name));
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack rewardIcon(CrateReward reward, List<String> extraLore, int amount) {
        ItemStack item = reward.item();
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.component(reward.displayName()));
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.addAll(components(extraLore));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private Map<String, String> rewardPlaceholders(CrateDefinition crate, CrateReward reward, double chance, int count) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("crate", crate.displayName());
        placeholders.put("crate_id", crate.id());
        placeholders.put("reward", reward.displayName());
        placeholders.put("reward_id", reward.id());
        placeholders.put("rarity", reward.rarity());
        placeholders.put("chance", String.format("%.2f", chance));
        placeholders.put("weight", Double.toString(reward.weight()));
        placeholders.put("amount", Integer.toString(reward.amount()));
        placeholders.put("count", Integer.toString(count));
        placeholders.put("material", materialName(reward.material()));
        placeholders.put("item", reward.giveItem() ? reward.amount() + "x " + materialName(reward.material()) : "");
        placeholders.put("commands", reward.commands().isEmpty() ? "" : Integer.toString(reward.commands().size()));
        placeholders.put("broadcast", reward.broadcast() ? "true" : "");
        return placeholders;
    }

    private List<String> renderGuiLore(Player player, String key, Map<String, String> placeholders,
                                       Map<String, List<String>> expansions) {
        List<String> rendered = new ArrayList<>();
        for (String rawLine : service.config().guiLore(key)) {
            String line = rawLine == null ? "" : rawLine;
            if (line.startsWith("[if_")) {
                int end = line.indexOf(']');
                if (end <= 4) {
                    continue;
                }
                String condition = line.substring(4, end);
                if (!truthy(placeholders.get(condition))) {
                    continue;
                }
                line = line.substring(end + 1).stripLeading();
            }
            List<String> expansion = expansions.get(line.trim());
            if (expansion != null) {
                rendered.addAll(expansion);
                continue;
            }
            rendered.add(applyPlaceholderApi(player, Text.render(line, placeholders)));
        }
        return rendered;
    }

    private String applyPlaceholderApi(Player player, String text) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    private boolean truthy(String value) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase("false") && !value.equals("0");
    }

    private String materialName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private RewardRequirementsView requirementsView(CrateReward reward) {
        List<String> lines = new ArrayList<>();
        var requirements = reward.requirements();
        if (!requirements.permissions().isEmpty()) {
            lines.add("&7Requires Permission");
        }
        if (!requirements.worlds().isEmpty()) {
            lines.add("&7Worlds: &f" + String.join(", ", requirements.worlds()));
        }
        if (requirements.oneTime()) {
            lines.add("&7One-time reward");
        }
        if (requirements.playerLimit() > 0) {
            lines.add("&7Player Limit: &f" + requirements.playerLimit());
        }
        if (requirements.globalLimit() > 0) {
            lines.add("&7Global Limit: &f" + requirements.globalLimit());
        }
        if (requirements.playerPeriodLimit() > 0 && requirements.playerPeriodMillis() > 0L) {
            lines.add("&7Player Window: &f" + requirements.playerPeriodLimit()
                    + " / " + DurationFormatter.compact(requirements.playerPeriodMillis()));
        }
        if (requirements.globalPeriodLimit() > 0 && requirements.globalPeriodMillis() > 0L) {
            lines.add("&7Global Window: &f" + requirements.globalPeriodLimit()
                    + " / " + DurationFormatter.compact(requirements.globalPeriodMillis()));
        }
        if (requirements.minOpenings() > 0) {
            lines.add("&7Requires Openings: &f" + requirements.minOpenings() + "+");
        }
        if (requirements.maxOpenings() > 0) {
            lines.add("&7Before Opening: &f" + requirements.maxOpenings());
        }
        return new RewardRequirementsView(lines);
    }

    private List<Component> components(List<String> lore) {
        return lore.stream().map(Text::component).toList();
    }

    private record ResultLine(CrateReward reward, int count) {
    }

    private record RewardRequirementsView(List<String> lines) {
    }
}
