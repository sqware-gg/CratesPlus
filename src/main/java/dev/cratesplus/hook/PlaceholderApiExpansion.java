package dev.cratesplus.hook;

import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateMilestone;
import dev.cratesplus.crate.CrateReward;
import dev.cratesplus.crate.CrateService;
import dev.cratesplus.util.DurationFormatter;
import java.util.Comparator;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaceholderApiExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final CrateService service;

    public PlaceholderApiExpansion(JavaPlugin plugin, CrateService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public String getAuthor() {
        return "SQWARE / Conflict";
    }

    @Override
    public String getIdentifier() {
        return "cratesplus";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "crate_count", "crates" -> Integer.toString(service.crates().size());
            case "block_count", "blocks" -> Integer.toString(service.blockCount());
            case "player_records", "players" -> Integer.toString(service.playerRecordCount());
            default -> dynamic(player, key);
        };
    }

    private String dynamic(OfflinePlayer player, String key) {
        if (key.startsWith("virtual_keys_")) {
            return Integer.toString(virtualKeys(player, key.substring("virtual_keys_".length())));
        }
        if (key.startsWith("physical_keys_")) {
            return Integer.toString(physicalKeys(player, key.substring("physical_keys_".length())));
        }
        if (key.startsWith("total_keys_") || key.startsWith("keys_")) {
            String crateId = key.startsWith("total_keys_")
                    ? key.substring("total_keys_".length())
                    : key.substring("keys_".length());
            return Integer.toString(virtualKeys(player, crateId) + physicalKeys(player, crateId));
        }
        if (key.startsWith("openings_")) {
            return player == null ? "0" : Integer.toString(service.openings(player.getUniqueId(), key.substring("openings_".length())));
        }
        if (key.startsWith("cooldown_seconds_")) {
            return player == null ? "0" : Long.toString(service.cooldownRemainingMillis(
                    player.getUniqueId(), key.substring("cooldown_seconds_".length())) / 1000L);
        }
        if (key.startsWith("cooldown_")) {
            return player == null ? "" : DurationFormatter.compact(service.cooldownRemainingMillis(
                    player.getUniqueId(), key.substring("cooldown_".length())));
        }
        if (key.startsWith("has_key_")) {
            String crateId = key.substring("has_key_".length());
            return virtualKeys(player, crateId) + physicalKeys(player, crateId) > 0 ? "yes" : "no";
        }
        if (key.startsWith("next_milestone_")) {
            return nextMilestone(player, key.substring("next_milestone_".length()));
        }
        if (key.startsWith("reward_global_reset_seconds_")) {
            CrateRewardKey rewardKey = crateRewardKey(key.substring("reward_global_reset_seconds_".length()));
            return rewardKey == null ? "0" : Long.toString(service.globalRewardLimitResetMillis(
                    rewardKey.crate().id(), rewardKey.reward().id()) / 1000L);
        }
        if (key.startsWith("reward_global_reset_")) {
            CrateRewardKey rewardKey = crateRewardKey(key.substring("reward_global_reset_".length()));
            return rewardKey == null ? "" : DurationFormatter.compact(service.globalRewardLimitResetMillis(
                    rewardKey.crate().id(), rewardKey.reward().id()));
        }
        if (key.startsWith("reward_global_remaining_")) {
            CrateRewardKey rewardKey = crateRewardKey(key.substring("reward_global_remaining_".length()));
            return rewardKey == null ? "0" : formatRemaining(service.globalRewardLimitRemaining(
                    rewardKey.crate().id(), rewardKey.reward().id()));
        }
        if (key.startsWith("reward_reset_seconds_")) {
            CrateRewardKey rewardKey = crateRewardKey(key.substring("reward_reset_seconds_".length()));
            return player == null || rewardKey == null ? "0" : Long.toString(service.rewardLimitResetMillis(
                    player.getUniqueId(), rewardKey.crate().id(), rewardKey.reward().id()) / 1000L);
        }
        if (key.startsWith("reward_reset_")) {
            CrateRewardKey rewardKey = crateRewardKey(key.substring("reward_reset_".length()));
            return player == null || rewardKey == null ? "" : DurationFormatter.compact(service.rewardLimitResetMillis(
                    player.getUniqueId(), rewardKey.crate().id(), rewardKey.reward().id()));
        }
        if (key.startsWith("reward_remaining_")) {
            CrateRewardKey rewardKey = crateRewardKey(key.substring("reward_remaining_".length()));
            return player == null || rewardKey == null ? "0" : formatRemaining(service.rewardLimitRemaining(
                    player.getUniqueId(), rewardKey.crate().id(), rewardKey.reward().id()));
        }
        return null;
    }

    private int virtualKeys(OfflinePlayer player, String crateId) {
        return player == null ? 0 : service.virtualKeys(player.getUniqueId(), crateId);
    }

    private int physicalKeys(OfflinePlayer player, String crateId) {
        if (player == null || !(player.getPlayer() instanceof Player online)) {
            return 0;
        }
        return service.physicalKeys(online, crateId);
    }

    private String nextMilestone(OfflinePlayer player, String crateId) {
        if (player == null) {
            return "";
        }
        CrateDefinition crate = service.crate(crateId);
        if (crate == null || crate.milestones().isEmpty()) {
            return "";
        }
        int openings = service.openings(player.getUniqueId(), crate.id());
        return crate.milestones().values().stream()
                .map(milestone -> nextMilestoneValue(milestone, openings))
                .filter(value -> value > openings)
                .min(Comparator.naturalOrder())
                .map(value -> Integer.toString(value))
                .orElse("");
    }

    private int nextMilestoneValue(CrateMilestone milestone, int openings) {
        if (!milestone.repeatable()) {
            return milestone.openings();
        }
        int interval = milestone.openings();
        return ((openings / interval) + 1) * interval;
    }

    private CrateRewardKey crateRewardKey(String input) {
        for (String crateId : service.crateIds().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            String prefix = crateId.toLowerCase(Locale.ROOT) + "_";
            if (!input.startsWith(prefix)) {
                continue;
            }
            CrateDefinition crate = service.crate(crateId);
            if (crate == null) {
                continue;
            }
            CrateReward reward = service.reward(crate, input.substring(prefix.length()));
            if (reward != null) {
                return new CrateRewardKey(crate, reward);
            }
        }
        return null;
    }

    private String formatRemaining(int remaining) {
        return remaining == Integer.MAX_VALUE ? "unlimited" : Integer.toString(Math.max(0, remaining));
    }

    private record CrateRewardKey(CrateDefinition crate, CrateReward reward) {
    }
}
