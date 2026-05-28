package dev.cratesplus.command;

import dev.cratesplus.crate.CrateActionResult;
import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateService;
import dev.cratesplus.gui.CratesGui;
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
            case "list", "browse" -> gui.openList(player, 0);
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
        if (args.length != 2) {
            service.send(player, "usage-preview", Map.of());
            return;
        }
        if (service.crate(args[1]) == null) {
            service.send(player, "invalid-crate", Map.of());
            return;
        }
        gui.openPreview(player, args[1], 0);
    }

    private void open(Player player, String[] args) {
        if (!player.hasPermission("cratesplus.open")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        if (args.length < 2 || args.length > 3) {
            service.send(player, "usage-open", Map.of());
            return;
        }
        int amount = 1;
        if (args.length == 3) {
            if (args[2].equalsIgnoreCase("all")) {
                amount = Integer.MAX_VALUE;
            } else {
                Integer parsed = parseInt(args[2]);
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
            gui.openResult(player, result.crate(), result.rewards());
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
            return filter(List.of("list", "preview", "open", "keys", "help"), args[0]);
        }
        if (args.length == 2 && List.of("preview", "open", "keys").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(service.crateIds(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            return filter(List.of("1", "5", "10", "all"), args[2]);
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
