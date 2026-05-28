package dev.cratesplus.api.event;

import dev.cratesplus.crate.CrateKeyMode;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CrateKeyChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final String crateId;
    private final CrateKeyMode mode;
    private final int change;
    private final int newVirtualBalance;
    private final long timestampMillis;

    public CrateKeyChangeEvent(UUID playerUuid, String playerName, String crateId, CrateKeyMode mode,
                               int change, int newVirtualBalance, long timestampMillis) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.crateId = crateId;
        this.mode = mode;
        this.change = change;
        this.newVirtualBalance = newVirtualBalance;
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

    public CrateKeyMode mode() {
        return mode;
    }

    public int change() {
        return change;
    }

    public int newVirtualBalance() {
        return newVirtualBalance;
    }

    public long timestampMillis() {
        return timestampMillis;
    }
}
