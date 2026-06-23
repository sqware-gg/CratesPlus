package dev.cratesplus.command;

import dev.cratesplus.crate.CrateActionResult;
import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateReward;
import dev.cratesplus.crate.CrateService;
import dev.cratesplus.gui.CratesGui;
import dev.cratesplus.util.DurationFormatter;
import dev.cratesplus.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CratesCommand implements CommandExecutor, TabCompleter {
    private final CrateService service;
    private final CratesGui gui;

    public CratesCommand(CrateService service, CratesGui gui) {
        this.service = service;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return true;
        }
        if (!player.hasPermission("cratesplus.use")) {
            service.send(player, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            gui.openList(player, 0);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list", "browse" -> {
                if (args.length >= 2 && textArgument(args[1])) {
                    listText(player);
                } else {
                    gui.openList(player, 0);
                }
            }
            case "text" -> listText(player);
            case "preview", "rewards" -> preview(player, args);
            case "open" -> open(player, args);
            case "keys", "key" -> keys(player, args);
            case "help", "commands", "?" -> service.send(player, "help", Map.of());
            default -> service.send(player, "help", Map.of());
        }
        return true;
    }

    private void preview(Player player, String[] args) {
        if (!player.hasPermission("cratesplus.preview")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        if (args.length < 2 || args.length > 3) {
            service.send(player, "usage-preview", Map.of());
            return;
        }
        CrateDefinition crate = service.crate(args[1]);
        if (crate == null) {
            service.send(player, "invalid-crate", Map.of());
            return;
        }
        if (args.length == 3 && textArgument(args[2])) {
            previewText(player, crate);
            return;
        }
        gui.openPreview(player, args[1], 0);
    }

    private void open(Player player, String[] args) {
        if (!player.hasPermission("cratesplus.open")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        if (args.length < 2 || args.length > 4) {
            service.send(player, "usage-open", Map.of());
            return;
        }
        int amount = 1;
        boolean textOnly = false;
        for (int index = 2; index < args.length; index++) {
            if (textArgument(args[index])) {
                textOnly = true;
                continue;
            }
            if (args[index].equalsIgnoreCase("all")) {
                amount = Integer.MAX_VALUE;
            } else {
                Integer parsed = parseInt(args[index]);
                if (parsed == null || parsed <= 0) {
                    service.send(player, "invalid-amount", Map.of("max", Integer.toString(service.config().maxMassOpen())));
                    return;
                }
                amount = parsed;
            }
        }
        CrateActionResult result = service.open(player, args[1], amount);
        service.send(player, result);
        if (result.success()) {
            if (textOnly) {
                rewardsText(player, result.crate(), result.rewards());
            } else {
                gui.openResult(player, result.crate(), result.rewards());
            }
        }
    }

    private void keys(Player player, String[] args) {
        if (args.length > 2) {
            service.send(player, "help", Map.of());
            return;
        }
        if (args.length == 2) {
            CrateDefinition crate = service.crate(args[1]);
            if (crate == null) {
                service.send(player, "invalid-crate", Map.of());
                return;
            }
            sendKeys(player, crate);
            return;
        }
        boolean any = false;
        for (CrateDefinition crate : service.crates()) {
            int virtual = service.virtualKeys(player.getUniqueId(), crate.id());
            int physical = service.physicalKeys(player, crate.id());
            if (virtual > 0 || physical > 0) {
                sendKeys(player, crate);
                any = true;
            }
        }
        if (!any) {
            service.send(player, "keys-all-empty", Map.of());
        }
    }

    private void sendKeys(Player player, CrateDefinition crate) {
        service.send(player, "keys", Map.of(
                "crate", crate.displayName(),
                "virtual", Integer.toString(service.virtualKeys(player.getUniqueId(), crate.id())),
                "physical", Integer.toString(service.physicalKeys(player, crate.id()))
        ));
    }

    private void listText(Player player) {
        line(player, "&fCrates");
        for (CrateDefinition crate : service.crates().stream()
                .sorted(java.util.Comparator.comparing(CrateDefinition::id))
                .toList()) {
            String cooldown = crate.cooldownMillis() <= 0L ? "none" : DurationFormatter.compact(crate.cooldownMillis());
            line(player, "&#2b98fd" + crate.id() + " &8- &f" + crate.displayName()
                    + " &8| &7keys &f" + service.availableKeys(player, crate.id())
                    + " &8| &7cost &f" + service.formatMoney(crate.openCost())
                    + " &8| &7cooldown &f" + cooldown
                    + " &8- &7/crates preview " + crate.id() + " text");
        }
    }

    private void previewText(Player player, CrateDefinition crate) {
        line(player, "&fRewards: " + crate.displayName() + " &8(&7" + crate.rewards().size() + " rewards&8)");
        double totalWeight = crate.totalWeight();
        for (CrateReward reward : crate.rewards()) {
            double chance = totalWeight <= 0.0D ? 0.0D : (reward.weight() / totalWeight) * 100.0D;
            line(player, "&8- &#2b98fd" + reward.id()
                    + " &8| &f" + reward.displayName()
                    + " &8| &7" + reward.rarity()
                    + " &8| &7" + String.format(Locale.ROOT, "%.2f", chance) + "%");
        }
        line(player, "&7Open with &f/crates open " + crate.id() + " [amount] text&7.");
    }

    private void rewardsText(Player player, CrateDefinition crate, List<CrateReward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }
        line(player, "&fOpened: " + crate.displayName() + " &8(&7" + rewards.size() + " reward(s)&8)");
        rewards.stream()
                .collect(java.util.stream.Collectors.groupingBy(CrateReward::displayName, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()))
                .forEach((name, amount) -> line(player, "&8- &f" + name + " &8x&#2b98fd" + amount));
    }

    private boolean textArgument(String value) {
        return value.equalsIgnoreCase("text")
                || value.equalsIgnoreCase("chat")
                || value.equalsIgnoreCase("nogui")
                || value.equalsIgnoreCase("no-gui");
    }

    private void line(CommandSender sender, String line) {
        sender.sendMessage(Text.color(service.config().prefix() + line));
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("cratesplus.use")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("list", "text", "preview", "open", "keys", "help"), args[0]);
        }
        if (args.length == 2 && List.of("list", "browse").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("text", "chat", "nogui"), args[1]);
        }
        if (args.length == 2 && List.of("preview", "open", "keys").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(service.crateIds(), args[1]);
        }
        if (args.length == 3 && List.of("preview", "rewards").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("text", "chat", "nogui"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            return filter(List.of("1", "5", "10", "all", "text", "nogui"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("open")) {
            return filter(List.of("text", "chat", "nogui", "no-gui", "1", "5", "10", "all"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }
}
