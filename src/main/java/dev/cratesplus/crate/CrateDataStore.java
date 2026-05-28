package dev.cratesplus.crate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CrateDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerCrateData> players = new HashMap<>();
    private final Map<LocationKey, String> blocks = new LinkedHashMap<>();
    private final Map<String, Integer> globalRewardClaims = new HashMap<>();
    private final Map<String, List<Long>> globalRewardClaimTimes = new HashMap<>();

    public CrateDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crates-data.yml");
        reload();
    }

    public synchronized void reload() {
        players.clear();
        blocks.clear();
        globalRewardClaims.clear();
        globalRewardClaimTimes.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadPlayers(yaml.getConfigurationSection("players"));
        loadBlocks(yaml.getConfigurationSection("blocks"));
        readIntMap(yaml.getConfigurationSection("global-reward-claims"), globalRewardClaims);
        readLongListMap(yaml.getConfigurationSection("global-reward-claim-times"), globalRewardClaimTimes);
    }

    public synchronized PlayerCrateData playerData(UUID uuid) {
        return players.computeIfAbsent(uuid, ignored -> new PlayerCrateData());
    }

    public synchronized Optional<PlayerCrateData> existingPlayerData(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
    }

    public synchronized Collection<PlayerCrateData> playerRecords() {
        return players.values();
    }

    public synchronized int playerRecordCount() {
        return players.size();
    }

    public synchronized int blockCount() {
        return blocks.size();
    }

    public synchronized int globalRewardClaims(String rewardKey) {
        return Math.max(0, globalRewardClaims.getOrDefault(rewardKey, 0));
    }

    public synchronized int addGlobalRewardClaim(String rewardKey) {
        int next = globalRewardClaims(rewardKey) + 1;
        globalRewardClaims.put(rewardKey, next);
        return next;
    }

    public synchronized void addGlobalRewardClaimTime(String rewardKey, long timestamp) {
        if (timestamp <= 0L) {
            return;
        }
        globalRewardClaimTimes.computeIfAbsent(rewardKey, ignored -> new ArrayList<>()).add(timestamp);
    }

    public synchronized int globalRewardClaimsSince(String rewardKey, long since) {
        List<Long> timestamps = globalRewardClaimTimes.get(rewardKey);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (long timestamp : timestamps) {
            if (timestamp >= since) {
                count++;
            }
        }
        return count;
    }

    public synchronized long globalRewardClaimWindowResetMillis(String rewardKey, long windowMillis, int limit, long now) {
        if (windowMillis <= 0L || limit <= 0) {
            return 0L;
        }
        List<Long> timestamps = globalRewardClaimTimes.get(rewardKey);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0L;
        }
        long since = now - windowMillis;
        List<Long> recent = timestamps.stream()
                .filter(timestamp -> timestamp >= since)
                .sorted()
                .toList();
        if (recent.size() < limit) {
            return 0L;
        }
        long resetsAt = recent.get(Math.max(0, recent.size() - limit)) + windowMillis;
        return Math.max(0L, resetsAt - now);
    }

    public synchronized void resetGlobalRewardClaim(String rewardKey) {
        globalRewardClaims.remove(rewardKey);
        globalRewardClaimTimes.remove(rewardKey);
    }

    public synchronized void resetGlobalRewardClaimsForCrate(String crateId) {
        globalRewardClaims.keySet().removeIf(key -> key.startsWith(crateId + "."));
        globalRewardClaimTimes.keySet().removeIf(key -> key.startsWith(crateId + "."));
    }

    public synchronized void pruneRewardClaimTimes(long oldestAllowed) {
        players.values().forEach(data -> data.pruneRewardClaimTimes(oldestAllowed));
        globalRewardClaimTimes.values().forEach(timestamps -> timestamps.removeIf(timestamp -> timestamp < oldestAllowed));
        globalRewardClaimTimes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public synchronized Map<LocationKey, String> blocks() {
        return Map.copyOf(blocks);
    }

    public synchronized Optional<String> crateAt(LocationKey location) {
        return Optional.ofNullable(blocks.get(location));
    }

    public synchronized void setBlock(LocationKey location, String crateId) {
        blocks.put(location, crateId);
    }

    public synchronized Optional<String> removeBlock(LocationKey location) {
        return Optional.ofNullable(blocks.remove(location));
    }

    public synchronized boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerCrateData> entry : players.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerCrateData data = entry.getValue();
            writeIntMap(yaml, path + ".virtual-keys", data.virtualKeys());
            writeLongMap(yaml, path + ".cooldowns", data.cooldownUntil());
            writeIntMap(yaml, path + ".openings", data.openings());
            writeIntMap(yaml, path + ".reward-claims", data.rewardClaims());
            writeLongListMap(yaml, path + ".reward-claim-times", data.rewardClaimTimes());
        }
        writeIntMap(yaml, "global-reward-claims", globalRewardClaims);
        writeLongListMap(yaml, "global-reward-claim-times", globalRewardClaimTimes);
        int index = 0;
        for (Map.Entry<LocationKey, String> entry : blocks.entrySet()) {
            LocationKey location = entry.getKey();
            String path = "blocks." + index++;
            yaml.set(path + ".world", location.world());
            yaml.set(path + ".x", location.x());
            yaml.set(path + ".y", location.y());
            yaml.set(path + ".z", location.z());
            yaml.set(path + ".crate", entry.getValue());
        }

        Path target = file.toPath();
        Path directory = target.getParent();
        Path backup = target.resolveSibling(file.getName() + ".bak");
        Path temp = null;
        try {
            Files.createDirectories(directory);
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = Files.createTempFile(directory, file.getName(), ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            moveIntoPlace(temp, target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save crates-data.yml: " + e.getMessage());
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    private void loadPlayers(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection playerSection = section.getConfigurationSection(key);
                if (playerSection == null) {
                    continue;
                }
                PlayerCrateData data = new PlayerCrateData();
                readIntMap(playerSection.getConfigurationSection("virtual-keys"), data.virtualKeys());
                readLongMap(playerSection.getConfigurationSection("cooldowns"), data.cooldownUntil());
                readIntMap(playerSection.getConfigurationSection("openings"), data.openings());
                readIntMap(playerSection.getConfigurationSection("reward-claims"), data.rewardClaims());
                readLongListMap(playerSection.getConfigurationSection("reward-claim-times"), data.rewardClaimTimes());
                players.put(uuid, data);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring crate player data with invalid UUID: " + key);
            }
        }
    }

    private void loadBlocks(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection blockSection = section.getConfigurationSection(key);
            if (blockSection == null) {
                continue;
            }
            String world = blockSection.getString("world", "");
            String crate = blockSection.getString("crate", "");
            if (world.isBlank() || crate.isBlank()) {
                continue;
            }
            blocks.put(new LocationKey(
                    world,
                    blockSection.getInt("x"),
                    blockSection.getInt("y"),
                    blockSection.getInt("z")
            ), crate);
        }
    }

    private void readIntMap(ConfigurationSection section, Map<String, Integer> target) {
        readIntMap(section, target, "");
    }

    private void readIntMap(ConfigurationSection section, Map<String, Integer> target, String prefix) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String flatKey = prefix.isBlank() ? key : prefix + "." + key;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                readIntMap(child, target, flatKey);
                continue;
            }
            int value = section.getInt(key, 0);
            if (value > 0) {
                target.put(flatKey, value);
            }
        }
    }

    private void readLongMap(ConfigurationSection section, Map<String, Long> target) {
        readLongMap(section, target, "");
    }

    private void readLongMap(ConfigurationSection section, Map<String, Long> target, String prefix) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String flatKey = prefix.isBlank() ? key : prefix + "." + key;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                readLongMap(child, target, flatKey);
                continue;
            }
            long value = section.getLong(key, 0L);
            if (value > 0L) {
                target.put(flatKey, value);
            }
        }
    }

    private void readLongListMap(ConfigurationSection section, Map<String, List<Long>> target) {
        readLongListMap(section, target, "");
    }

    private void readLongListMap(ConfigurationSection section, Map<String, List<Long>> target, String prefix) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String flatKey = prefix.isBlank() ? key : prefix + "." + key;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                readLongListMap(child, target, flatKey);
                continue;
            }
            List<Long> timestamps = section.getLongList(key).stream()
                    .filter(timestamp -> timestamp > 0L)
                    .toList();
            if (!timestamps.isEmpty()) {
                target.put(flatKey, new ArrayList<>(timestamps));
            }
        }
    }

    private void writeIntMap(YamlConfiguration yaml, String path, Map<String, Integer> map) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() > 0) {
                yaml.set(path + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    private void writeLongMap(YamlConfiguration yaml, String path, Map<String, Long> map) {
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (entry.getValue() > 0L) {
                yaml.set(path + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    private void writeLongListMap(YamlConfiguration yaml, String path, Map<String, List<Long>> map) {
        for (Map.Entry<String, List<Long>> entry : map.entrySet()) {
            List<Long> timestamps = entry.getValue().stream()
                    .filter(timestamp -> timestamp > 0L)
                    .toList();
            if (!timestamps.isEmpty()) {
                yaml.set(path + "." + entry.getKey(), timestamps);
            }
        }
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
