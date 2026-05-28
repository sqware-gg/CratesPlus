package dev.cratesplus.command;

import dev.cratesplus.crate.CrateActionResult;
import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateKeyMode;
import dev.cratesplus.crate.CrateReward;
import dev.cratesplus.crate.CrateService;
import dev.cratesplus.crate.LocationKey;
import dev.cratesplus.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CratesPlusCommand implements CommandExecutor, TabCompleter {
    private static final int SIMULATION_LIMIT = 100_000;

    private final CrateService service;

    public CratesPlusCommand(CrateService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cratesplus.admin")) {
            service.send(sender, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            service.send(sender, "usage-admin", Map.of());
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats", "status" -> stats(sender);
            case "reload" -> {
                service.reload();
                service.send(sender, "reloaded", Map.of());
            }
            case "save" -> {
                service.save();
                service.send(sender, "saved", Map.of());
            }
            case "givekey" -> giveKey(sender, args);
            case "takekey" -> takeKey(sender, args);
            case "setkey" -> setKey(sender, args);
            case "giveall" -> giveAll(sender, args);
            case "openfor", "forceopen" -> openFor(sender, args);
            case "givecrate" -> giveCrate(sender, args);
            case "setblock" -> setBlock(sender, args);
            case "removeblock" -> removeBlock(sender);
            case "listblocks", "blocks" -> listBlocks(sender, args);
            case "info" -> info(sender, args);
            case "simulate" -> simulate(sender, args);
            case "resetcooldown" -> resetCooldown(sender, args);
            case "resetopenings" -> resetOpenings(sender, args);
            case "resetlimit", "resetlimits" -> resetLimit(sender, args);
            default -> service.send(sender, "usage-admin", Map.of());
        }
        return true;
    }

    private void stats(CommandSender sender) {
        service.send(sender, "status", Map.of(
                "crates", Integer.toString(service.crates().size()),
                "blocks", Integer.toString(service.blockCount()),
                "players", Integer.toString(service.playerRecordCount()),
                "economy", service.economyName()
        ));
    }

    private void giveKey(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 5) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(sender, args[2]);
        Integer amount = positiveInt(sender, args[3]);
        CrateKeyMode mode = args.length == 5 ? mode(args[4]) : CrateKeyMode.VIRTUAL;
        if (crate == null || amount == null || mode == null) {
            if (mode == null) {
                service.send(sender, "invalid-key-mode", Map.of());
            }
            return;
        }
        if (mode == CrateKeyMode.PHYSICAL) {
            Player target = onlinePlayer(sender, args[1]);
            if (target != null) {
                service.send(sender, service.giveKeys(target, crate, amount, mode));
            }
            return;
        }
        service.send(sender, service.giveVirtualKeys(offlinePlayer(args[1]), crate, amount));
    }

    private void takeKey(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 5) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(sender, args[2]);
        Integer amount = positiveInt(sender, args[3]);
        CrateKeyMode mode = args.length == 5 ? mode(args[4]) : CrateKeyMode.VIRTUAL;
        if (crate == null || amount == null || mode == null) {
            if (mode == null) {
                service.send(sender, "invalid-key-mode", Map.of());
            }
            return;
        }
        if (mode == CrateKeyMode.PHYSICAL) {
            Player target = onlinePlayer(sender, args[1]);
            if (target != null) {
                service.send(sender, service.takePhysicalKeys(target, crate, amount));
            }
            return;
        }
        service.send(sender, service.takeVirtualKeys(offlinePlayer(args[1]), crate, amount));
    }

    private void setKey(CommandSender sender, String[] args) {
        if (args.length != 4) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(sender, args[2]);
        Integer amount = nonNegativeInt(sender, args[3]);
        if (crate == null || amount == null) {
            return;
        }
        service.send(sender, service.setVirtualKeys(offlinePlayer(args[1]), crate, amount));
    }

    private void giveAll(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(sender, args[1]);
        Integer amount = positiveInt(sender, args[2]);
        CrateKeyMode mode = args.length == 4 ? mode(args[3]) : CrateKeyMode.VIRTUAL;
        if (crate == null || amount == null || mode == null) {
            if (mode == null) {
                service.send(sender, "invalid-key-mode", Map.of());
            }
            return;
        }
        int count = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (mode == CrateKeyMode.PHYSICAL) {
                service.giveKeys(target, crate, amount, mode);
            } else {
                service.giveVirtualKeys(target, crate, amount);
            }
            count++;
        }
        service.send(sender, "key-given-all", Map.of(
                "players", Integer.toString(count),
                "amount", Integer.toString(amount),
                "crate", crate.displayName(),
                "mode", mode.name().toLowerCase(Locale.ROOT)
        ));
    }

    private void openFor(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 5) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        Player target = onlinePlayer(sender, args[1]);
        CrateDefinition crate = crate(sender, args[2]);
        int amount = 1;
        boolean force = false;
        if (args.length >= 4) {
            if (forceFlag(args[3])) {
                force = true;
            } else {
                Integer parsed = openAmount(sender, args[3]);
                if (parsed == null) {
                    return;
                }
                amount = parsed;
            }
        }
        if (args.length == 5) {
            force = forceFlag(args[4]);
        }
        if (target == null || crate == null) {
            return;
        }
        CrateActionResult result = service.openFor(target, crate.id(), amount, force);
        service.send(sender, result);
        if (!sender.equals(target)) {
            service.send(target, result);
        }
    }

    private void giveCrate(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        Player target = onlinePlayer(sender, args[1]);
        CrateDefinition crate = crate(sender, args[2]);
        Integer amount = args.length == 4 ? positiveInt(sender, args[3]) : 1;
        if (target == null || crate == null || amount == null) {
            return;
        }
        service.send(sender, service.giveCrateItem(target, crate, amount));
    }

    private void setBlock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (args.length != 2) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(player, args[1]);
        if (crate == null) {
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType().isAir()) {
            service.send(player, "no-target-block", Map.of());
            return;
        }
        service.send(player, service.setBlock(block, crate));
    }

    private void removeBlock(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType().isAir()) {
            service.send(player, "no-target-block", Map.of());
            return;
        }
        service.send(player, service.removeBlock(block));
    }

    private void listBlocks(CommandSender sender, String[] args) {
        CrateDefinition filter = null;
        if (args.length > 2) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        if (args.length == 2) {
            filter = crate(sender, args[1]);
            if (filter == null) {
                return;
            }
        }
        CrateDefinition filterCrate = filter;
        List<Map.Entry<LocationKey, String>> blocks = service.linkedBlocks().entrySet().stream()
                .filter(entry -> filterCrate == null || entry.getValue().equals(filterCrate.id()))
                .sorted(Comparator.comparing(entry -> entry.getKey().world() + ":" + entry.getKey().x() + ":" + entry.getKey().y() + ":" + entry.getKey().z()))
                .toList();
        line(sender, "&fCrate Blocks &8(&7" + blocks.size() + "&8)");
        for (Map.Entry<LocationKey, String> entry : blocks.stream().limit(12).toList()) {
            LocationKey location = entry.getKey();
            line(sender, "&#2b98fd" + entry.getValue() + " &8- &7" + location.world()
                    + " &f" + location.x() + " " + location.y() + " " + location.z());
        }
        if (blocks.size() > 12) {
            line(sender, "&7Showing 12 of &f" + blocks.size() + "&7 linked blocks.");
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(sender, args[1]);
        if (crate == null) {
            return;
        }
        long blockCount = service.linkedBlocks().values().stream().filter(crate.id()::equals).count();
        line(sender, "&fCrate Info: " + crate.displayName());
        line(sender, "&7ID: &#2b98fd" + crate.id() + " &8| &7Rewards: &f" + crate.rewards().size()
                + " &8| &7Weight: &f" + String.format("%.2f", crate.totalWeight()));
        line(sender, "&7Cost: &f" + service.formatMoney(crate.openCost()) + " &8| &7Cooldown: &f"
                + (crate.cooldownMillis() <= 0 ? "none" : crate.cooldownMillis() / 1000L + "s")
                + " &8| &7Blocks: &f" + blockCount);
        for (CrateReward reward : crate.rewards().stream().limit(8).toList()) {
            double chance = crate.totalWeight() <= 0.0D ? 0.0D : (reward.weight() / crate.totalWeight()) * 100.0D;
            line(sender, "&8- &f" + reward.id() + " &7" + reward.rarity() + " &#2b98fd"
                    + String.format("%.2f", chance) + "% &8(weight " + reward.weight() + ")");
        }
    }

    private void simulate(CommandSender sender, String[] args) {
        if (args.length != 3) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(sender, args[1]);
        Integer rolls = positiveInt(sender, args[2]);
        if (crate == null || rolls == null) {
            return;
        }
        rolls = Math.min(rolls, SIMULATION_LIMIT);
        int totalRolls = rolls;
        Map<String, Integer> results = service.simulate(crate, rolls);
        line(sender, "&fSimulation: " + crate.displayName() + " &8(&7" + rolls + " rolls&8)");
        results.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    double percent = (entry.getValue() / (double) totalRolls) * 100.0D;
                    line(sender, "&8- &#2b98fd" + entry.getKey() + " &7" + entry.getValue()
                            + " &8(&f" + String.format("%.2f", percent) + "%&8)");
                });
    }

    private void resetCooldown(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = null;
        if (args.length == 3 && !args[2].equalsIgnoreCase("all")) {
            crate = crate(sender, args[2]);
            if (crate == null) {
                return;
            }
        }
        service.send(sender, service.resetCooldown(offlinePlayer(args[1]), crate));
    }

    private void resetOpenings(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = null;
        if (args.length == 3 && !args[2].equalsIgnoreCase("all")) {
            crate = crate(sender, args[2]);
            if (crate == null) {
                return;
            }
        }
        service.send(sender, service.resetOpenings(offlinePlayer(args[1]), crate));
    }

    private void resetLimit(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        CrateDefinition crate = crate(sender, args[2]);
        if (crate == null) {
            return;
        }
        String rewardId = args.length == 4 ? args[3] : "all";
        service.send(sender, service.resetRewardLimit(offlinePlayer(args[1]), crate, rewardId));
    }

    private CrateDefinition crate(CommandSender sender, String id) {
        CrateDefinition crate = service.crate(id);
        if (crate == null) {
            service.send(sender, "invalid-crate", Map.of());
        }
        return crate;
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer offlinePlayer(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    private Player onlinePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            service.send(sender, "invalid-player", Map.of());
        }
        return target;
    }

    private CrateKeyMode mode(String value) {
        try {
            return CrateKeyMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean forceFlag(String value) {
        return value.equalsIgnoreCase("force") || value.equalsIgnoreCase("-f") || value.equalsIgnoreCase("--force");
    }

    private Integer openAmount(CommandSender sender, String value) {
        if (value.equalsIgnoreCase("all")) {
            return Integer.MAX_VALUE;
        }
        return positiveInt(sender, value);
    }

    private Integer positiveInt(CommandSender sender, String value) {
        Integer parsed = parseInt(value);
        if (parsed == null || parsed <= 0) {
            service.send(sender, "invalid-amount", Map.of("max", Integer.toString(service.config().maxMassOpen())));
            return null;
        }
        return parsed;
    }

    private Integer nonNegativeInt(CommandSender sender, String value) {
        Integer parsed = parseInt(value);
        if (parsed == null || parsed < 0) {
            service.send(sender, "invalid-amount", Map.of("max", Integer.toString(service.config().maxMassOpen())));
            return null;
        }
        return parsed;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void line(CommandSender sender, String line) {
        sender.sendMessage(Text.color(service.config().prefix() + line));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cratesplus.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of(
                    "stats", "reload", "save", "givekey", "takekey", "setkey", "giveall", "openfor",
                    "givecrate", "setblock", "removeblock", "listblocks", "info", "simulate",
                    "resetcooldown", "resetopenings", "resetlimit"
            ), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("givekey", "takekey", "setkey", "openfor", "givecrate",
                "resetcooldown", "resetopenings", "resetlimit").contains(sub)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if ((args.length == 3 && List.of("givekey", "takekey", "setkey", "openfor", "givecrate", "resetlimit").contains(sub))
                || (args.length == 2 && List.of("setblock", "listblocks", "info", "simulate", "giveall").contains(sub))) {
            return filter(service.crateIds(), args[args.length - 1]);
        }
        if (args.length == 3 && List.of("resetcooldown", "resetopenings").contains(sub)) {
            List<String> values = new ArrayList<>(service.crateIds());
            values.add("all");
            return filter(values, args[2]);
        }
        if (args.length == 4 && sub.equals("resetlimit")) {
            CrateDefinition crate = service.crate(args[2]);
            if (crate == null) {
                return List.of("all");
            }
            List<String> rewards = new ArrayList<>(crate.rewards().stream().map(CrateReward::id).toList());
            rewards.add("all");
            return filter(rewards, args[3]);
        }
        if (args.length == 4 && List.of("givekey", "takekey", "setkey", "givecrate", "openfor", "simulate", "giveall").contains(sub)) {
            return filter(List.of("1", "5", "10", "64"), args[3]);
        }
        if ((args.length == 5 && List.of("givekey", "takekey", "giveall").contains(sub))) {
            return filter(List.of("virtual", "physical"), args[4]);
        }
        if (args.length == 5 && sub.equals("openfor")) {
            return filter(List.of("force", "-f"), args[4]);
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
