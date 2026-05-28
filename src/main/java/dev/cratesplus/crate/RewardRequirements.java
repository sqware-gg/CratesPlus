package dev.cratesplus.crate;

import java.util.List;

public record RewardRequirements(
        List<String> permissions,
        List<String> worlds,
        int minOpenings,
        int maxOpenings,
        boolean oneTime,
        int playerLimit,
        int globalLimit,
        int playerPeriodLimit,
        long playerPeriodMillis,
        int globalPeriodLimit,
        long globalPeriodMillis
) {
    public RewardRequirements {
        permissions = List.copyOf(permissions);
        worlds = List.copyOf(worlds);
        minOpenings = Math.max(0, minOpenings);
        maxOpenings = Math.max(0, maxOpenings);
        playerLimit = Math.max(0, playerLimit);
        globalLimit = Math.max(0, globalLimit);
        playerPeriodLimit = Math.max(0, playerPeriodLimit);
        playerPeriodMillis = Math.max(0L, playerPeriodMillis);
        globalPeriodLimit = Math.max(0, globalPeriodLimit);
        globalPeriodMillis = Math.max(0L, globalPeriodMillis);
    }
}
