package dev.cratesplus.config;

import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateMilestone;
import dev.cratesplus.crate.CrateParticles;
import dev.cratesplus.crate.CrateReward;
import dev.cratesplus.crate.RewardRequirements;
import dev.cratesplus.util.DurationParser;
import dev.cratesplus.util.Text;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class CratesPlusConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration defaultConfig;
    private Map<String, CrateDefinition> crates = new LinkedHashMap<>();

    public CratesPlusConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        defaultConfig = loadBundledConfig();
        crates = loadCrates(config.getConfigurationSection("crates"));
    }

    public Map<String, CrateDefinition> crates() {
        return crates;
    }

    public CrateDefinition crate(String id) {
        if (id == null) {
            return null;
        }
        return crates.get(normalizeId(id));
    }

    public long saveIntervalTicks() {
        return Math.max(20L, safeMultiply(Math.max(1L,
                config.getLong("storage.save-interval-seconds", 300L)), 20L));
    }

    public long claimHistoryRetentionMillis() {
        long configured = DurationParser.millis(config.getString("storage.claim-history-retention", "90d"),
                7_776_000_000L);
        long largestPeriod = crates.values().stream()
                .flatMap(crate -> crate.rewards().stream())
                .map(CrateReward::requirements)
                .mapToLong(requirements -> Math.max(requirements.playerPeriodMillis(), requirements.globalPeriodMillis()))
                .max()
                .orElse(0L);
        return Math.max(configured, largestPeriod);
    }

    public boolean logsEnabled() {
        return config.getBoolean("logs.enabled", true);
    }

    public String logFileName() {
        String fileName = config.getString("logs.file", "open-history.log");
        return fileName == null || fileName.isBlank() ? "open-history.log" : fileName;
    }

    public boolean virtualFirst() {
        return config.getBoolean("keys.virtual-first", true);
    }

    public Material physicalKeyMaterial() {
        return material("keys.physical-key-material", Material.TRIPWIRE_HOOK);
    }

    public Material crateItemMaterial() {
        return material("keys.crate-item-material", Material.CHEST);
    }

    public int maxMassOpen() {
        return Math.max(1, config.getInt("opening.max-mass-open", 64));
    }

    public boolean dropOverflowItems() {
        return config.getBoolean("opening.drop-overflow-items", true);
    }

    public boolean particlesEnabled() {
        return config.getBoolean("effects.particles-enabled", true);
    }

    public Particle particle() {
        return matchParticle(config.getString("effects.particle", "ENCHANT"), Particle.ENCHANT);
    }

    public long particleIntervalTicks() {
        return Math.max(5L, config.getLong("effects.particle-interval-ticks", 20L));
    }

    public int particleCount() {
        return Math.max(1, config.getInt("effects.particle-count", 8));
    }

    public double particleOffsetX() {
        return nonNegative(config.getDouble("effects.particle-offset-x", 0.35D));
    }

    public double particleOffsetY() {
        return nonNegative(config.getDouble("effects.particle-offset-y", 0.25D));
    }

    public double particleOffsetZ() {
        return nonNegative(config.getDouble("effects.particle-offset-z", 0.35D));
    }

    public double particleSpeed() {
        return nonNegative(config.getDouble("effects.particle-speed", 0.01D));
    }

    public Sound sound(String key, Sound fallback) {
        String value = config.getString("effects." + key, fallback.name());
        try {
            return Sound.valueOf(value == null ? fallback.name() : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public String guiTitle(String key) {
        return config.getString("gui." + key.toLowerCase(Locale.ROOT), "");
    }

    public List<String> guiLore(String key) {
        String path = "gui." + key.toLowerCase(Locale.ROOT);
        List<String> lines = config.getStringList(path);
        if (!lines.isEmpty()) {
            return lines;
        }
        return defaultConfig.getStringList(path);
    }

    public List<String> keyLore() {
        return config.getStringList("items.key-lore");
    }

    public List<String> crateItemLore() {
        return config.getStringList("items.crate-item-lore");
    }

    public String announcement(String key) {
        return config.getString("announcements." + key, "");
    }

    public String prefix() {
        return message("prefix");
    }

    public String message(String key) {
        String path = "messages." + key;
        String message = config.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        message = defaultConfig.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Missing message: " + key;
    }

    private Map<String, CrateDefinition> loadCrates(ConfigurationSection section) {
        Map<String, CrateDefinition> loaded = new LinkedHashMap<>();
        if (section == null) {
            plugin.getLogger().warning("No crates are configured.");
            return loaded;
        }
        for (String rawId : section.getKeys(false).stream().sorted().toList()) {
            String id = normalizeId(rawId);
            ConfigurationSection crateSection = section.getConfigurationSection(rawId);
            if (crateSection == null) {
                continue;
            }
            Material icon = matchMaterial(crateSection.getString("icon"), Material.CHEST);
            List<CrateReward> rewards = loadRewards(id, crateSection.getConfigurationSection("rewards"));
            if (rewards.isEmpty()) {
                plugin.getLogger().warning("Ignoring crate " + id + " because it has no valid rewards.");
                continue;
            }
            int previewRows = rows(crateSection.getInt("preview-rows", 6));
            int openingRows = rows(crateSection.getInt("opening-rows", 3));
            CrateDefinition crate = new CrateDefinition(
                    id,
                    crateSection.getString("display-name", id),
                    crateSection.getStringList("description"),
                    icon,
                    crateSection.getString("key-name", id + " Key"),
                    crateSection.getString("crate-item-name", id + " Crate"),
                    crateSection.getString("permission", ""),
                    safeMultiply(Math.max(0L, crateSection.getLong("cooldown-seconds", 0L)), 1000L),
                    nonNegative(crateSection.getDouble("open-cost", 0.0D)),
                    previewRows,
                    openingRows,
                    crateSection.getBoolean("allow-mass-open", true),
                    loadParticles(crateSection),
                    rewards,
                    loadMilestones(crateSection.getConfigurationSection("milestones"))
            );
            loaded.put(id, crate);
        }
        return loaded;
    }

    private List<CrateReward> loadRewards(String crateId, ConfigurationSection section) {
        List<CrateReward> rewards = new ArrayList<>();
        if (section == null) {
            return rewards;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection rewardSection = section.getConfigurationSection(rawId);
            if (rewardSection == null) {
                continue;
            }
            ItemStack item = buildRewardItem(rawId, rewardSection);
            if (!item.getType().isItem() || item.getType().isAir()) {
                plugin.getLogger().warning("Ignoring reward " + rawId + " in crate " + crateId + ": invalid material.");
                continue;
            }
            double weight = rewardSection.getDouble("weight", 1.0D);
            if (!Double.isFinite(weight) || weight <= 0.0D) {
                plugin.getLogger().warning("Ignoring reward " + rawId + " in crate " + crateId + ": weight must be positive.");
                continue;
            }
            rewards.add(new CrateReward(
                    normalizeId(rawId),
                    rewardSection.getString("display-name", rawId),
                    item,
                    rewardSection.getBoolean("give-item", true),
                    weight,
                    rewardSection.getString("rarity", "COMMON").toUpperCase(Locale.ROOT),
                    rewardSection.getStringList("commands"),
                    rewardSection.getBoolean("broadcast", false),
                    loadRequirements(rewardSection.getConfigurationSection("requirements"))
            ));
        }
        rewards.sort(Comparator.comparing(CrateReward::id));
        return rewards;
    }

    private ItemStack buildRewardItem(String rewardId, ConfigurationSection section) {
        Material material = matchMaterial(section.getString("item.material", section.getString("material")), Material.CHEST);
        int amount = Math.max(1, section.getInt("item.amount", section.getInt("amount", 1)));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = section.getString("item.name", section.getString("name", ""));
        if (name != null && !name.isBlank()) {
            meta.displayName(Text.component(name));
        }
        List<String> lore = section.getStringList("item.lore");
        if (lore.isEmpty()) {
            lore = section.getStringList("lore");
        }
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(Text::component).toList());
        }
        int customModelData = section.getInt("item.custom-model-data", section.getInt("custom-model-data", 0));
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (section.getBoolean("item.unbreakable", section.getBoolean("unbreakable", false))) {
            meta.setUnbreakable(true);
        }
        addItemFlags(meta, section.getStringList("item.flags"));
        if (section.getStringList("item.flags").isEmpty()) {
            addItemFlags(meta, section.getStringList("flags"));
        }
        addEnchantments(meta, section.getConfigurationSection("item.enchantments"));
        if (section.getConfigurationSection("item.enchantments") == null) {
            addEnchantments(meta, section.getConfigurationSection("enchantments"));
        }
        item.setItemMeta(meta);
        return item;
    }

    private RewardRequirements loadRequirements(ConfigurationSection section) {
        if (section == null) {
            return new RewardRequirements(List.of(), List.of(), 0, 0, false, 0, 0, 0, 0L, 0, 0L);
        }
        List<String> permissions = new ArrayList<>(section.getStringList("permissions"));
        String permission = section.getString("permission", "");
        if (!permission.isBlank()) {
            permissions.add(permission);
        }
        long period = duration(section, "period", 0L);
        int periodLimit = section.getInt("period-limit", 0);
        long playerPeriod = duration(section, "player-period", period);
        int playerPeriodLimit = section.getInt("player-period-limit", periodLimit);
        long globalPeriod = duration(section, "global-period", 0L);
        int globalPeriodLimit = section.getInt("global-period-limit", 0);
        return new RewardRequirements(
                permissions,
                section.getStringList("worlds"),
                section.getInt("min-openings", 0),
                section.getInt("max-openings", 0),
                section.getBoolean("one-time", false),
                section.getInt("player-limit", 0),
                section.getInt("global-limit", 0),
                playerPeriodLimit,
                playerPeriod,
                globalPeriodLimit,
                globalPeriod
        );
    }

    private void addItemFlags(ItemMeta meta, List<String> flags) {
        for (String rawFlag : flags) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(rawFlag.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid item flag: " + rawFlag);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void addEnchantments(ItemMeta meta, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String rawKey : section.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(rawKey.toLowerCase(Locale.ROOT)));
            if (enchantment == null) {
                plugin.getLogger().warning("Ignoring invalid enchantment: " + rawKey);
                continue;
            }
            meta.addEnchant(enchantment, Math.max(1, section.getInt(rawKey, 1)), true);
        }
    }

    private Map<Integer, CrateMilestone> loadMilestones(ConfigurationSection section) {
        Map<Integer, CrateMilestone> milestones = new LinkedHashMap<>();
        if (section == null) {
            return milestones;
        }
        for (String key : section.getKeys(false)) {
            try {
                int openings = Integer.parseInt(key);
                if (openings <= 0) {
                    continue;
                }
                ConfigurationSection milestoneSection = section.getConfigurationSection(key);
                if (milestoneSection == null) {
                    continue;
                }
                milestones.put(openings, new CrateMilestone(
                        openings,
                        milestoneSection.getBoolean("repeatable", false),
                        milestoneSection.getString("message", ""),
                        milestoneSection.getStringList("commands")
                ));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Ignoring milestone " + key + ": milestone key must be a number.");
            }
        }
        return milestones;
    }

    private CrateParticles loadParticles(ConfigurationSection crateSection) {
        ConfigurationSection particles = crateSection.getConfigurationSection("particles");
        boolean enabled = booleanOverride(crateSection, particles, particlesEnabled(),
                "particles-enabled", "enabled");
        String particleName = stringOverride(crateSection, particles, null,
                "particle", "particle-type", "type");
        Particle particle = particleName == null ? particle() : matchParticle(particleName, particle());
        int count = intOverride(crateSection, particles, particleCount(),
                "particle-count", "count");
        double offsetX = doubleOverride(crateSection, particles, particleOffsetX(),
                "particle-offset-x", "offset-x", "spread-x");
        double offsetY = doubleOverride(crateSection, particles, particleOffsetY(),
                "particle-offset-y", "offset-y", "spread-y");
        double offsetZ = doubleOverride(crateSection, particles, particleOffsetZ(),
                "particle-offset-z", "offset-z", "spread-z");
        double speed = doubleOverride(crateSection, particles, particleSpeed(),
                "particle-speed", "speed");
        return new CrateParticles(enabled, particle, Math.max(1, count),
                nonNegative(offsetX), nonNegative(offsetY), nonNegative(offsetZ), nonNegative(speed));
    }

    private FileConfiguration loadBundledConfig() {
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load bundled config defaults: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    private Material material(String path, Material fallback) {
        return matchMaterial(config.getString(path), fallback);
    }

    private Material matchMaterial(String value, Material fallback) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        return material == null ? fallback : material;
    }

    private Particle matchParticle(String value, Particle fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring invalid particle: " + value);
            return fallback;
        }
    }

    private int rows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private double nonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0D ? value : 0.0D;
    }

    private boolean booleanOverride(ConfigurationSection root, ConfigurationSection nested,
                                    boolean fallback, String rootPath, String nestedPath) {
        if (nested != null && nested.contains(nestedPath)) {
            return nested.getBoolean(nestedPath, fallback);
        }
        if (root.contains(rootPath)) {
            return root.getBoolean(rootPath, fallback);
        }
        return fallback;
    }

    private String stringOverride(ConfigurationSection root, ConfigurationSection nested,
                                  String fallback, String... paths) {
        if (nested != null) {
            for (String path : paths) {
                String value = nested.getString(path);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        for (String path : paths) {
            String value = root.getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private int intOverride(ConfigurationSection root, ConfigurationSection nested,
                            int fallback, String... paths) {
        if (nested != null) {
            for (String path : paths) {
                if (nested.contains(path)) {
                    return nested.getInt(path, fallback);
                }
            }
        }
        for (String path : paths) {
            if (root.contains(path)) {
                return root.getInt(path, fallback);
            }
        }
        return fallback;
    }

    private double doubleOverride(ConfigurationSection root, ConfigurationSection nested,
                                  double fallback, String... paths) {
        if (nested != null) {
            for (String path : paths) {
                if (nested.contains(path)) {
                    return nested.getDouble(path, fallback);
                }
            }
        }
        for (String path : paths) {
            if (root.contains(path)) {
                return root.getDouble(path, fallback);
            }
        }
        return fallback;
    }

    private long safeMultiply(long value, long multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private long duration(ConfigurationSection section, String path, long fallback) {
        return DurationParser.millis(section.getString(path, ""), fallback);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
