package dev.cratesplus.api.event;

import java.util.List;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CrateOpenEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final String crateId;
    private final String crateName;
    private final int amount;
    private final List<String> rewardIds;
    private final long timestampMillis;

    public CrateOpenEvent(UUID playerUuid, String playerName, String crateId, String crateName,
                          int amount, List<String> rewardIds, long timestampMillis) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.crateId = crateId;
        this.crateName = crateName;
        this.amount = amount;
        this.rewardIds = List.copyOf(rewardIds);
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

    public String crateName() {
        return crateName;
    }

    public int amount() {
        return amount;
    }

    public List<String> rewardIds() {
        return rewardIds;
    }

    public long timestampMillis() {
        return timestampMillis;
    }
}
