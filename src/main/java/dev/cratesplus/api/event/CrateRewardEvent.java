package dev.cratesplus.api.event;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CrateRewardEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final String crateId;
    private final String rewardId;
    private final String rewardName;
    private final Material material;
    private final int amount;
    private final String rarity;
    private final long timestampMillis;

    public CrateRewardEvent(UUID playerUuid, String playerName, String crateId, String rewardId,
                            String rewardName, Material material, int amount, String rarity, long timestampMillis) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.crateId = crateId;
        this.rewardId = rewardId;
        this.rewardName = rewardName;
        this.material = material;
        this.amount = amount;
        this.rarity = rarity;
        this.timestampMillis = timestampMillis;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public String crateId() {
        return crateId;
    }

    public String rewardId() {
        return rewardId;
    }

    public String rewardName() {
        return rewardName;
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public String rarity() {
        return rarity;
    }

    public long timestampMillis() {
        return timestampMillis;
    }
}
