package dev.cratesplus.crate;

import dev.cratesplus.api.event.CrateKeyChangeEvent;
import dev.cratesplus.api.event.CrateOpenEvent;
import dev.cratesplus.api.event.CrateRewardEvent;
import dev.cratesplus.config.CratesPlusConfig;
import dev.cratesplus.economy.EconomyService;
import dev.cratesplus.economy.EconomyTransaction;
import dev.cratesplus.util.DurationFormatter;
import dev.cratesplus.util.InventoryUtil;
import dev.cratesplus.util.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CrateService {
    private static final String ITEM_TYPE_KEY = "key";
    private static final String ITEM_TYPE_CRATE = "crate";

    private final JavaPlugin plugin;
    private final CratesPlusConfig config;
    private final CrateDataStore store;
    private final EconomyService economy;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey crateIdKey;
    private BukkitTask saveTask;
    private BukkitTask particleTask;

    public CrateService(JavaPlugin plugin, CratesPlusConfig config, CrateDataStore store, EconomyService economy) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.economy = economy;
        this.itemTypeKey = new NamespacedKey(plugin, "item_type");
        this.crateIdKey = new NamespacedKey(plugin, "crate_id");
    }

    public void start() {
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, store::save,
                config.saveIntervalTicks(), config.saveIntervalTicks());
        startParticles();
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        save();
    }

    public void reload() {
        stop();
        config.reload();
        economy.refresh();
        store.reload();
        start();
    }

    public CratesPlusConfig config() {
        return config;
    }

    public Collection<CrateDefinition> crates() {
        return config.crates().values();
    }

    public List<String> crateIds() {
        return crates().stream().map(CrateDefinition::id).sorted().toList();
    }

    public CrateDefinition crate(String crateId) {
        return config.crate(crateId);
    }

    public Optional<String> crateAt(Block block) {
        return store.crateAt(LocationKey.from(block));
    }

    public int virtualKeys(UUID uuid, String crateId) {
        return store.existingPlayerData(uuid)
                .map(data -> data.virtualKeys(normalizeId(crateId)))
                .orElse(0);
    }

    public int openings(UUID uuid, String crateId) {
        return store.existingPlayerData(uuid)
                .map(data -> data.openings(normalizeId(crateId)))
                .orElse(0);
    }

    public long cooldownRemainingMillis(UUID uuid, String crateId) {
        long until = store.existingPlayerData(uuid)
                .map(data -> data.cooldownUntil(normalizeId(crateId)))
                .orElse(0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public int physicalKeys(Player player, String crateId) {
        String normalized = normalizeId(crateId);
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isTaggedItem(item, ITEM_TYPE_KEY, normalized)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public int availableKeys(Player player, String crateId) {
        return virtualKeys(player.getUniqueId(), crateId) + physicalKeys(player, crateId);
    }

    public ItemStack keyItem(CrateDefinition crate, int amount) {
        ItemStack item = new ItemStack(config.physicalKeyMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.component(crate.keyName()));
        meta.lore(renderLore(config.keyLore(), placeholders(crate, null, 0)));
        meta.addItemFlags(ItemFlag.values());
        tag(meta, ITEM_TYPE_KEY, crate.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack crateItem(CrateDefinition crate, int amount) {
        ItemStack item = new ItemStack(config.crateItemMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.component(crate.crateItemName()));
        meta.lore(renderLore(config.crateItemLore(), placeholders(crate, null, 0)));
        meta.addItemFlags(ItemFlag.values());
        tag(meta, ITEM_TYPE_CRATE, crate.id());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<String> keyCrateId(ItemStack item) {
        return taggedCrateId(item, ITEM_TYPE_KEY);
    }

    public Optional<String> crateItemCrateId(ItemStack item) {
        return taggedCrateId(item, ITEM_TYPE_CRATE);
    }

    public CrateActionResult giveKeys(Player target, CrateDefinition crate, int amount, CrateKeyMode mode) {
        int safeAmount = Math.max(1, amount);
        if (mode == CrateKeyMode.PHYSICAL) {
            giveStacked(target, keyItem(crate, 1), safeAmount);
            callKeyChange(target, crate.id(), mode, safeAmount);
            send(target, "physical-key-received", Map.of(
                    "crate", crate.displayName(),
                    "amount", Integer.toString(safeAmount)
            ));
            return CrateActionResult.success("physical-key-given", Map.of(
                    "player", target.getName(),
                    "crate", crate.displayName(),
                    "amount", Integer.toString(safeAmount)
            ), crate, List.of());
        }
        int balance = store.playerData(target.getUniqueId()).addVirtualKeys(crate.id(), safeAmount);
        store.save();
        callKeyChange(target, crate.id(), mode, safeAmount, balance);
        send(target, "key-received", Map.of(
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount),
                "balance", Integer.toString(balance)
        ));
        return CrateActionResult.success("key-given", Map.of(
                "player", target.getName(),
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount)
        ), crate, List.of());
    }

    public CrateActionResult giveVirtualKeys(OfflinePlayer target, CrateDefinition crate, int amount) {
        int safeAmount = Math.max(1, amount);
        int balance = store.playerData(target.getUniqueId()).addVirtualKeys(crate.id(), safeAmount);
        store.save();
        callKeyChange(target, crate.id(), CrateKeyMode.VIRTUAL, safeAmount, balance);
        notifyIfOnline(target, "key-received", Map.of(
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount),
                "balance", Integer.toString(balance)
        ));
        return CrateActionResult.success("key-given", Map.of(
                "player", playerName(target),
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount)
        ), crate, List.of());
    }

    public CrateActionResult setVirtualKeys(OfflinePlayer target, CrateDefinition crate, int amount) {
        int safeAmount = Math.max(0, amount);
        PlayerCrateData data = store.playerData(target.getUniqueId());
        int previous = data.virtualKeys(crate.id());
        data.setVirtualKeys(crate.id(), safeAmount);
        store.save();
        callKeyChange(target, crate.id(), CrateKeyMode.VIRTUAL, safeAmount - previous, safeAmount);
        notifyIfOnline(target, "key-balance-set", Map.of(
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount)
        ));
        return CrateActionResult.success("key-set", Map.of(
                "player", playerName(target),
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount)
        ), crate, List.of());
    }

    public CrateActionResult takeVirtualKeys(OfflinePlayer target, CrateDefinition crate, int amount) {
        int safeAmount = Math.max(1, amount);
        int taken = store.playerData(target.getUniqueId()).takeVirtualKeys(crate.id(), safeAmount);
        int balance = virtualKeys(target.getUniqueId(), crate.id());
        store.save();
        callKeyChange(target, crate.id(), CrateKeyMode.VIRTUAL, -taken, balance);
        notifyIfOnline(target, "key-removed", Map.of(
                "crate", crate.displayName(),
                "amount", Integer.toString(taken),
                "balance", Integer.toString(balance)
        ));
        return CrateActionResult.success("key-taken", Map.of(
                "player", playerName(target),
                "crate", crate.displayName(),
                "amount", Integer.toString(taken)
        ), crate, List.of());
    }

    public CrateActionResult takePhysicalKeys(Player target, CrateDefinition crate, int amount) {
        int safeAmount = Math.max(1, amount);
        int available = physicalKeys(target, crate.id());
        int taken = Math.min(available, safeAmount);
        if (taken > 0) {
            removePhysicalKeys(target, crate.id(), taken);
            callKeyChange(target, crate.id(), CrateKeyMode.PHYSICAL, -taken);
            send(target, "physical-key-removed", Map.of(
                    "crate", crate.displayName(),
                    "amount", Integer.toString(taken)
            ));
        }
        return CrateActionResult.success("physical-key-taken", Map.of(
                "player", target.getName(),
                "crate", crate.displayName(),
                "amount", Integer.toString(taken)
        ), crate, List.of());
    }

    public CrateActionResult giveCrateItem(Player target, CrateDefinition crate, int amount) {
        int safeAmount = Math.max(1, amount);
        giveStacked(target, crateItem(crate, 1), safeAmount);
        send(target, "crate-item-received", Map.of(
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount)
        ));
        return CrateActionResult.success("crate-item-given", Map.of(
                "player", target.getName(),
                "crate", crate.displayName(),
                "amount", Integer.toString(safeAmount)
        ), crate, List.of());
    }

    public CrateActionResult setBlock(Block block, CrateDefinition crate) {
        store.setBlock(LocationKey.from(block), crate.id());
        store.save();
        return CrateActionResult.success("block-set", Map.of("crate", crate.displayName()), crate, List.of());
    }

    public CrateActionResult removeBlock(Block block) {
        Optional<String> removed = store.removeBlock(LocationKey.from(block));
        store.save();
        CrateDefinition crate = removed.map(this::crate).orElse(null);
        return CrateActionResult.success("block-removed", Map.of(), crate, List.of());
    }

    public synchronized CrateActionResult open(Player player, String crateId, int requestedAmount) {
        return open(player, crateId, requestedAmount, false);
    }

    public synchronized CrateActionResult openFor(Player player, String crateId, int requestedAmount, boolean force) {
        return open(player, crateId, requestedAmount, force);
    }

    private CrateActionResult open(Player player, String crateId, int requestedAmount, boolean force) {
        CrateDefinition crate = crate(crateId);
        if (crate == null) {
            return CrateActionResult.failure("invalid-crate", Map.of());
        }
        if (!force && !crate.hasPermission(player)) {
            return CrateActionResult.failure("crate-permission", Map.of("crate", crate.displayName()));
        }
        if (crate.totalWeight() <= 0.0D) {
            return CrateActionResult.failure("invalid-crate", Map.of());
        }

        int amount;
        if (force) {
            amount = requestedAmount == Integer.MAX_VALUE ? config.maxMassOpen() : requestedAmount;
            amount = Math.max(1, Math.min(amount, config.maxMassOpen()));
        } else {
            int availableKeys = availableKeys(player, crate.id());
            if (availableKeys <= 0) {
                playSound(player, config.sound("sound-no-key", Sound.BLOCK_NOTE_BLOCK_BASS), 0.8F, 0.8F);
                return CrateActionResult.failure("no-key", Map.of("crate", crate.displayName()));
            }
            amount = resolveOpenAmount(crate, requestedAmount, availableKeys);
            if (availableKeys < amount) {
                return CrateActionResult.failure("not-enough-keys", Map.of(
                        "crate", crate.displayName(),
                        "keys", Integer.toString(availableKeys)
                ));
            }
        }
        if (amount <= 0) {
            return CrateActionResult.failure("invalid-amount", Map.of("max", Integer.toString(config.maxMassOpen())));
        }

        PlayerCrateData data = store.playerData(player.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldownUntil = data.cooldownUntil(crate.id());
        if (!force && cooldownUntil > now) {
            return CrateActionResult.failure("cooldown", Map.of(
                    "crate", crate.displayName(),
                    "time", DurationFormatter.compact(cooldownUntil - now)
            ));
        }
        int possibleOpens = force ? amount : possibleOpenCount(player, crate, data, amount);
        if (possibleOpens <= 0) {
            return CrateActionResult.failure("no-eligible-rewards", Map.of("crate", crate.displayName()));
        }
        amount = Math.min(amount, possibleOpens);

        double totalCost = force ? 0.0D : crate.openCost() * amount;
        if (!Double.isFinite(totalCost)) {
            return CrateActionResult.failure("transaction-failed", Map.of("reason", "invalid cost"));
        }
        if (totalCost > 0.0D && !economy.available()) {
            return CrateActionResult.failure("economy-unavailable", Map.of());
        }
        if (totalCost > 0.0D && !economy.has(player, totalCost)) {
            return CrateActionResult.failure("not-enough-money", Map.of("cost", economy.format(totalCost)));
        }
        EconomyTransaction withdraw = economy.withdraw(player, totalCost);
        if (!withdraw.success()) {
            return CrateActionResult.failure("transaction-failed", Map.of("reason", withdraw.errorMessage()));
        }

        if (!force && !consumeKeys(player, crate.id(), amount)) {
            economy.deposit(player, totalCost);
            return CrateActionResult.failure("not-enough-keys", Map.of(
                    "crate", crate.displayName(),
                    "keys", Integer.toString(availableKeys(player, crate.id()))
            ));
        }

        List<CrateReward> rewards = new ArrayList<>();
        for (int index = 0; index < amount; index++) {
            CrateReward reward = force ? rollRaw(crate) : roll(crate, player, data);
            if (reward == null) {
                break;
            }
            rewards.add(reward);
            grantReward(player, crate, reward);
            String rewardKey = rewardKey(crate, reward);
            data.addRewardClaim(rewardKey);
            data.addRewardClaimTime(rewardKey, now);
            store.addGlobalRewardClaim(rewardKey);
            store.addGlobalRewardClaimTime(rewardKey, now);
            int openings = data.addOpening(crate.id());
            handleMilestone(player, crate, openings);
        }
        if (!force && crate.cooldownMillis() > 0L) {
            data.setCooldownUntil(crate.id(), now + crate.cooldownMillis());
        }
        save();

        playSound(player, config.sound("sound-open", Sound.ENTITY_PLAYER_LEVELUP), 1.0F, 1.0F);
        Bukkit.getPluginManager().callEvent(new CrateOpenEvent(
                player.getUniqueId(),
                player.getName(),
                crate.id(),
                crate.displayName(),
                rewards.size(),
                rewards.stream().map(CrateReward::id).toList(),
                System.currentTimeMillis()
        ));
        logOpening(player, crate, rewards);
        String messageKey = rewards.size() == 1 ? "opened" : "opened-multiple";
        String rewardName = rewards.size() == 1 ? rewards.getFirst().displayName() : Integer.toString(rewards.size());
        return CrateActionResult.success(messageKey, Map.of(
                "crate", crate.displayName(),
                "reward", rewardName,
                "amount", Integer.toString(rewards.size())
        ), crate, rewards);
    }

    public CrateActionResult resetCooldown(OfflinePlayer target, CrateDefinition crate) {
        PlayerCrateData data = store.playerData(target.getUniqueId());
        if (crate == null) {
            data.resetAllCooldowns();
            store.save();
            return CrateActionResult.info("cooldown-reset-all", Map.of("player", playerName(target)));
        }
        data.resetCooldown(crate.id());
        store.save();
        return CrateActionResult.info("cooldown-reset", Map.of(
                "player", playerName(target),
                "crate", crate.displayName()
        ));
    }

    public CrateActionResult resetOpenings(OfflinePlayer target, CrateDefinition crate) {
        PlayerCrateData data = store.playerData(target.getUniqueId());
        if (crate == null) {
            data.resetAllOpenings();
            store.save();
            return CrateActionResult.info("openings-reset-all", Map.of("player", playerName(target)));
        }
        data.resetOpenings(crate.id());
        store.save();
        return CrateActionResult.info("openings-reset", Map.of(
                "player", playerName(target),
                "crate", crate.displayName()
        ));
    }

    public CrateActionResult resetRewardLimit(OfflinePlayer target, CrateDefinition crate, String rewardId) {
        PlayerCrateData data = store.playerData(target.getUniqueId());
        if (rewardId == null || rewardId.equalsIgnoreCase("all")) {
            data.resetRewardClaimsForCrate(crate.id());
            store.resetGlobalRewardClaimsForCrate(crate.id());
            store.save();
            return CrateActionResult.info("reward-limit-reset-all", Map.of(
                    "player", playerName(target),
                    "crate", crate.displayName()
            ));
        }
        CrateReward reward = reward(crate, rewardId);
        if (reward == null) {
            return CrateActionResult.failure("invalid-reward", Map.of());
        }
        String rewardKey = rewardKey(crate, reward);
        data.resetRewardClaims(rewardKey);
        store.resetGlobalRewardClaim(rewardKey);
        store.save();
        return CrateActionResult.info("reward-limit-reset", Map.of(
                "player", playerName(target),
                "crate", crate.displayName(),
                "reward", reward.displayName()
        ));
    }

    public CrateReward reward(CrateDefinition crate, String rewardId) {
        String normalized = normalizeId(rewardId);
        return crate.rewards().stream()
                .filter(reward -> reward.id().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public int rewardLimitRemaining(UUID uuid, String crateId, String rewardId) {
        CrateDefinition crate = crate(crateId);
        if (crate == null) {
            return 0;
        }
        CrateReward reward = reward(crate, rewardId);
        if (reward == null) {
            return 0;
        }
        PlayerCrateData data = store.existingPlayerData(uuid).orElseGet(PlayerCrateData::new);
        return remainingRewardRolls(crate, reward, data);
    }

    public long rewardLimitResetMillis(UUID uuid, String crateId, String rewardId) {
        CrateDefinition crate = crate(crateId);
        if (crate == null) {
            return 0L;
        }
        CrateReward reward = reward(crate, rewardId);
        if (reward == null) {
            return 0L;
        }
        PlayerCrateData data = store.existingPlayerData(uuid).orElseGet(PlayerCrateData::new);
        RewardRequirements requirements = reward.requirements();
        String rewardKey = rewardKey(crate, reward);
        long now = System.currentTimeMillis();
        long playerReset = data.rewardClaimWindowResetMillis(rewardKey,
                requirements.playerPeriodMillis(), requirements.playerPeriodLimit(), now);
        long globalReset = store.globalRewardClaimWindowResetMillis(rewardKey,
                requirements.globalPeriodMillis(), requirements.globalPeriodLimit(), now);
        return Math.max(playerReset, globalReset);
    }

    public int globalRewardLimitRemaining(String crateId, String rewardId) {
        CrateDefinition crate = crate(crateId);
        if (crate == null) {
            return 0;
        }
        CrateReward reward = reward(crate, rewardId);
        if (reward == null) {
            return 0;
        }
        return remainingGlobalRewardRolls(crate, reward);
    }

    public long globalRewardLimitResetMillis(String crateId, String rewardId) {
        CrateDefinition crate = crate(crateId);
        if (crate == null) {
            return 0L;
        }
        CrateReward reward = reward(crate, rewardId);
        if (reward == null) {
            return 0L;
        }
        RewardRequirements requirements = reward.requirements();
        return store.globalRewardClaimWindowResetMillis(rewardKey(crate, reward),
                requirements.globalPeriodMillis(), requirements.globalPeriodLimit(), System.currentTimeMillis());
    }

    public Map<LocationKey, String> linkedBlocks() {
        return store.blocks();
    }

    public Map<String, Integer> simulate(CrateDefinition crate, int rolls) {
        Map<String, Integer> results = new LinkedHashMap<>();
        int safeRolls = Math.max(1, Math.min(rolls, 100_000));
        for (int index = 0; index < safeRolls; index++) {
            CrateReward reward = rollRaw(crate);
            if (reward == null) {
                continue;
            }
            results.merge(reward.id(), 1, Integer::sum);
        }
        return results;
    }

    public void save() {
        long retentionMillis = config.claimHistoryRetentionMillis();
        if (retentionMillis > 0L) {
            store.pruneRewardClaimTimes(System.currentTimeMillis() - retentionMillis);
        }
        store.save();
    }

    public int blockCount() {
        return store.blockCount();
    }

    public int playerRecordCount() {
        return store.playerRecordCount();
    }

    public String economyName() {
        return economy.providerName();
    }

    public String formatMoney(double amount) {
        return economy.format(amount);
    }

    public void send(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        String rendered = Text.color(config.prefix() + Text.render(config.message(messageKey), placeholders));
        for (String line : rendered.split("\\R", -1)) {
            sender.sendMessage(line);
        }
    }

    public void send(CommandSender sender, CrateActionResult result) {
        send(sender, result.messageKey(), result.placeholders());
    }

    private void notifyIfOnline(OfflinePlayer target, String messageKey, Map<String, String> placeholders) {
        Player player = target.getPlayer();
        if (player != null && player.isOnline()) {
            send(player, messageKey, placeholders);
        }
    }

    public void sendRawPrefixed(CommandSender sender, String message, Map<String, String> placeholders) {
        if (message == null || message.isBlank()) {
            return;
        }
        String rendered = Text.color(config.prefix() + Text.render(message, placeholders));
        for (String line : rendered.split("\\R", -1)) {
            sender.sendMessage(line);
        }
    }

    private void startParticles() {
        if (!config.particlesEnabled()) {
            return;
        }
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drawParticles,
                config.particleIntervalTicks(), config.particleIntervalTicks());
    }

    private void drawParticles() {
        for (Map.Entry<LocationKey, String> entry : store.blocks().entrySet()) {
            if (crate(entry.getValue()) == null) {
                continue;
            }
            CrateDefinition crate = crate(entry.getValue());
            CrateParticles particles = crate.particles();
            if (!particles.enabled()) {
                continue;
            }
            LocationKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null) {
                continue;
            }
            Location location = new Location(world, key.x() + 0.5D, key.y() + 1.15D, key.z() + 0.5D);
            world.spawnParticle(particles.particle(), location, particles.count(),
                    particles.offsetX(), particles.offsetY(), particles.offsetZ(), particles.speed());
        }
    }

    private int resolveOpenAmount(CrateDefinition crate, int requestedAmount, int availableKeys) {
        if (!crate.allowMassOpen()) {
            return 1;
        }
        int max = Math.min(config.maxMassOpen(), availableKeys);
        if (requestedAmount == Integer.MAX_VALUE) {
            return max;
        }
        return Math.max(1, Math.min(requestedAmount, max));
    }

    private boolean consumeKeys(Player player, String crateId, int amount) {
        PlayerCrateData data = store.playerData(player.getUniqueId());
        int virtualAvailable = data.virtualKeys(crateId);
        int physicalAvailable = physicalKeys(player, crateId);
        if (virtualAvailable + physicalAvailable < amount) {
            return false;
        }
        int virtualToTake;
        if (config.virtualFirst()) {
            virtualToTake = Math.min(virtualAvailable, amount);
        } else {
            virtualToTake = Math.max(0, amount - physicalAvailable);
        }
        int physicalToTake = amount - virtualToTake;
        if (physicalToTake > 0 && !removePhysicalKeys(player, crateId, physicalToTake)) {
            return false;
        }
        if (virtualToTake > 0) {
            int balance = data.takeVirtualKeys(crateId, virtualToTake);
            callKeyChange(player, crateId, CrateKeyMode.VIRTUAL, -virtualToTake, balance);
        }
        if (physicalToTake > 0) {
            callKeyChange(player, crateId, CrateKeyMode.PHYSICAL, -physicalToTake);
        }
        return true;
    }

    private boolean removePhysicalKeys(Player player, String crateId, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!isTaggedItem(item, ITEM_TYPE_KEY, crateId)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        inventory.setStorageContents(contents);
        return remaining == 0;
    }

    private CrateReward roll(CrateDefinition crate, Player player, PlayerCrateData data) {
        List<CrateReward> eligible = eligibleRewards(player, crate, data);
        double totalWeight = eligible.stream().mapToDouble(CrateReward::weight).sum();
        if (eligible.isEmpty() || totalWeight <= 0.0D) {
            return null;
        }
        double target = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (CrateReward reward : eligible) {
            cursor += Math.max(0.0D, reward.weight());
            if (target <= cursor) {
                return reward;
            }
        }
        return eligible.getLast();
    }

    private CrateReward rollRaw(CrateDefinition crate) {
        double target = ThreadLocalRandom.current().nextDouble(crate.totalWeight());
        double cursor = 0.0D;
        for (CrateReward reward : crate.rewards()) {
            if (reward.weight() <= 0.0D) {
                continue;
            }
            cursor += reward.weight();
            if (target <= cursor) {
                return reward;
            }
        }
        return crate.rewards().isEmpty() ? null : crate.rewards().getLast();
    }

    private void grantReward(Player player, CrateDefinition crate, CrateReward reward) {
        if (reward.giveItem()) {
            ItemStack item = reward.item();
            InventoryUtil.give(player, item, config.dropOverflowItems());
        }
        for (String command : reward.commands()) {
            dispatchRewardCommand(command, player, crate, reward, 1, store.playerData(player.getUniqueId()).openings(crate.id()));
        }
        if (reward.broadcast()) {
            String template = config.announcement("reward-broadcast");
            if (template != null && !template.isBlank()) {
                Bukkit.broadcast(Text.component(Text.render(template, placeholders(crate, reward, 1, player))));
            }
        }
        Bukkit.getPluginManager().callEvent(new CrateRewardEvent(
                player.getUniqueId(),
                player.getName(),
                crate.id(),
                reward.id(),
                reward.displayName(),
                reward.material(),
                reward.amount(),
                reward.rarity(),
                System.currentTimeMillis()
        ));
    }

    private void handleMilestone(Player player, CrateDefinition crate, int openings) {
        for (CrateMilestone milestone : crate.milestones().values()) {
            boolean reached = milestone.repeatable()
                    ? openings % milestone.openings() == 0
                    : openings == milestone.openings();
            if (!reached) {
                continue;
            }
            Map<String, String> placeholders = placeholders(crate, null, openings, player);
            for (String command : milestone.commands()) {
                dispatchCommand(command, placeholders);
            }
            if (milestone.message() != null && !milestone.message().isBlank()) {
                sendRawPrefixed(player, milestone.message(), placeholders);
            } else {
                send(player, "milestone", Map.of(
                        "crate", crate.displayName(),
                        "amount", Integer.toString(openings)
                ));
            }
        }
    }

    private int possibleOpenCount(Player player, CrateDefinition crate, PlayerCrateData data, int requestedAmount) {
        int total = 0;
        for (CrateReward reward : eligibleRewards(player, crate, data)) {
            int remaining = remainingRewardRolls(crate, reward, data);
            if (remaining == Integer.MAX_VALUE) {
                return requestedAmount;
            }
            total += remaining;
            if (total >= requestedAmount) {
                return requestedAmount;
            }
        }
        return total;
    }

    private List<CrateReward> eligibleRewards(Player player, CrateDefinition crate, PlayerCrateData data) {
        return crate.rewards().stream()
                .filter(reward -> eligible(player, crate, reward, data))
                .toList();
    }

    private boolean eligible(Player player, CrateDefinition crate, CrateReward reward, PlayerCrateData data) {
        RewardRequirements requirements = reward.requirements();
        for (String permission : requirements.permissions()) {
            if (!permission.isBlank() && !player.hasPermission(permission)) {
                return false;
            }
        }
        if (!requirements.worlds().isEmpty()
                && requirements.worlds().stream().noneMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()))) {
            return false;
        }
        int openings = data.openings(crate.id());
        if (openings < requirements.minOpenings()) {
            return false;
        }
        if (requirements.maxOpenings() > 0 && openings >= requirements.maxOpenings()) {
            return false;
        }
        return remainingRewardRolls(crate, reward, data) > 0;
    }

    private int remainingRewardRolls(CrateDefinition crate, CrateReward reward, PlayerCrateData data) {
        RewardRequirements requirements = reward.requirements();
        String rewardKey = rewardKey(crate, reward);
        int remaining = Integer.MAX_VALUE;
        int playerLimit = requirements.oneTime() ? 1 : requirements.playerLimit();
        if (playerLimit > 0) {
            remaining = Math.min(remaining, Math.max(0, playerLimit - data.rewardClaims(rewardKey)));
        }
        if (requirements.globalLimit() > 0) {
            remaining = Math.min(remaining, Math.max(0, requirements.globalLimit() - store.globalRewardClaims(rewardKey)));
        }
        if (requirements.playerPeriodLimit() > 0 && requirements.playerPeriodMillis() > 0L) {
            int recentClaims = data.rewardClaimsSince(rewardKey, System.currentTimeMillis() - requirements.playerPeriodMillis());
            remaining = Math.min(remaining, Math.max(0, requirements.playerPeriodLimit() - recentClaims));
        }
        if (requirements.globalPeriodLimit() > 0 && requirements.globalPeriodMillis() > 0L) {
            int recentClaims = store.globalRewardClaimsSince(rewardKey, System.currentTimeMillis() - requirements.globalPeriodMillis());
            remaining = Math.min(remaining, Math.max(0, requirements.globalPeriodLimit() - recentClaims));
        }
        if (requirements.maxOpenings() > 0) {
            remaining = Math.min(remaining, Math.max(0, requirements.maxOpenings() - data.openings(crate.id())));
        }
        return remaining;
    }

    private int remainingGlobalRewardRolls(CrateDefinition crate, CrateReward reward) {
        RewardRequirements requirements = reward.requirements();
        String rewardKey = rewardKey(crate, reward);
        int remaining = Integer.MAX_VALUE;
        if (requirements.globalLimit() > 0) {
            remaining = Math.min(remaining, Math.max(0, requirements.globalLimit() - store.globalRewardClaims(rewardKey)));
        }
        if (requirements.globalPeriodLimit() > 0 && requirements.globalPeriodMillis() > 0L) {
            int recentClaims = store.globalRewardClaimsSince(rewardKey, System.currentTimeMillis() - requirements.globalPeriodMillis());
            remaining = Math.min(remaining, Math.max(0, requirements.globalPeriodLimit() - recentClaims));
        }
        return remaining;
    }

    private String rewardKey(CrateDefinition crate, CrateReward reward) {
        return crate.id() + "." + reward.id();
    }

    private void dispatchRewardCommand(String command, Player player, CrateDefinition crate, CrateReward reward,
                                       int amount, int openings) {
        dispatchCommand(command, placeholders(crate, reward, amount, player, openings));
    }

    private void dispatchCommand(String command, Map<String, String> placeholders) {
        String rendered = Text.render(command == null ? "" : command, placeholders).trim();
        if (rendered.isBlank()) {
            return;
        }
        if (rendered.startsWith("/")) {
            rendered = rendered.substring(1);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered);
    }

    private void giveStacked(Player target, ItemStack base, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, base.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = base.clone();
            stack.setAmount(Math.min(remaining, maxStack));
            InventoryUtil.give(target, stack, true);
            remaining -= stack.getAmount();
        }
    }

    private Optional<String> taggedCrateId(ItemStack item, String expectedType) {
        if (InventoryUtil.isEmpty(item) || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String type = container.get(itemTypeKey, PersistentDataType.STRING);
        String crateId = container.get(crateIdKey, PersistentDataType.STRING);
        if (!expectedType.equals(type) || crateId == null || crateId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(crateId);
    }

    private boolean isTaggedItem(ItemStack item, String expectedType, String crateId) {
        return taggedCrateId(item, expectedType)
                .map(id -> id.equals(normalizeId(crateId)))
                .orElse(false);
    }

    private void tag(ItemMeta meta, String type, String crateId) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(itemTypeKey, PersistentDataType.STRING, type);
        container.set(crateIdKey, PersistentDataType.STRING, normalizeId(crateId));
    }

    private List<Component> renderLore(List<String> lore, Map<String, String> placeholders) {
        return lore.stream()
                .map(line -> Text.component(Text.render(line, placeholders)))
                .toList();
    }

    private Map<String, String> placeholders(CrateDefinition crate, CrateReward reward, int amount) {
        return placeholders(crate, reward, amount, null, 0);
    }

    private Map<String, String> placeholders(CrateDefinition crate, CrateReward reward, int amount, Player player) {
        int openings = player == null ? 0 : store.playerData(player.getUniqueId()).openings(crate.id());
        return placeholders(crate, reward, amount, player, openings);
    }

    private Map<String, String> placeholders(CrateDefinition crate, CrateReward reward, int amount,
                                             Player player, int openings) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player == null ? "" : player.getName());
        placeholders.put("uuid", player == null ? "" : player.getUniqueId().toString());
        placeholders.put("crate", crate == null ? "" : crate.displayName());
        placeholders.put("crate_id", crate == null ? "" : crate.id());
        placeholders.put("reward", reward == null ? "" : reward.displayName());
        placeholders.put("reward_id", reward == null ? "" : reward.id());
        placeholders.put("amount", Integer.toString(amount));
        placeholders.put("openings", Integer.toString(openings));
        if (player != null && crate != null) {
            int virtual = virtualKeys(player.getUniqueId(), crate.id());
            int physical = physicalKeys(player, crate.id());
            placeholders.put("virtual_keys", Integer.toString(virtual));
            placeholders.put("physical_keys", Integer.toString(physical));
            placeholders.put("total_keys", Integer.toString(virtual + physical));
        } else {
            placeholders.put("virtual_keys", "0");
            placeholders.put("physical_keys", "0");
            placeholders.put("total_keys", "0");
        }
        return placeholders;
    }

    private void callKeyChange(Player player, String crateId, CrateKeyMode mode, int change) {
        callKeyChange(player, crateId, mode, change, virtualKeys(player.getUniqueId(), crateId));
    }

    private void callKeyChange(OfflinePlayer player, String crateId, CrateKeyMode mode, int change, int newVirtualBalance) {
        Bukkit.getPluginManager().callEvent(new CrateKeyChangeEvent(
                player.getUniqueId(),
                playerName(player),
                crateId,
                mode,
                change,
                newVirtualBalance,
                System.currentTimeMillis()
        ));
    }

    private void callKeyChange(Player player, String crateId, CrateKeyMode mode, int change, int newVirtualBalance) {
        Bukkit.getPluginManager().callEvent(new CrateKeyChangeEvent(
                player.getUniqueId(),
                player.getName(),
                crateId,
                mode,
                change,
                newVirtualBalance,
                System.currentTimeMillis()
        ));
    }

    private String playerName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString() : name;
    }

    private void logOpening(Player player, CrateDefinition crate, List<CrateReward> rewards) {
        if (!config.logsEnabled() || rewards.isEmpty()) {
            return;
        }
        Path path = plugin.getDataFolder().toPath().resolve(config.logFileName());
        String rewardList = String.join(",", rewards.stream().map(CrateReward::id).toList());
        String line = Instant.now() + "\t" + player.getUniqueId() + "\t" + player.getName()
                + "\t" + crate.id() + "\t" + rewards.size() + "\t" + rewardList + System.lineSeparator();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write crate opening log: " + e.getMessage());
        }
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
