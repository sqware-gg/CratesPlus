package dev.cratesplus.crate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerCrateData {
    private final Map<String, Integer> virtualKeys = new HashMap<>();
    private final Map<String, Long> cooldownUntil = new HashMap<>();
    private final Map<String, Integer> openings = new HashMap<>();
    private final Map<String, Integer> rewardClaims = new HashMap<>();
    private final Map<String, List<Long>> rewardClaimTimes = new HashMap<>();

    public Map<String, Integer> virtualKeys() {
        return virtualKeys;
    }

    public Map<String, Long> cooldownUntil() {
        return cooldownUntil;
    }

    public Map<String, Integer> openings() {
        return openings;
    }

    public Map<String, Integer> rewardClaims() {
        return rewardClaims;
    }

    public Map<String, List<Long>> rewardClaimTimes() {
        return rewardClaimTimes;
    }

    public int virtualKeys(String crateId) {
        return Math.max(0, virtualKeys.getOrDefault(crateId, 0));
    }

    public int addVirtualKeys(String crateId, int amount) {
        int next = Math.max(0, virtualKeys(crateId) + amount);
        if (next == 0) {
            virtualKeys.remove(crateId);
        } else {
            virtualKeys.put(crateId, next);
        }
        return next;
    }

    public int takeVirtualKeys(String crateId, int amount) {
        int current = virtualKeys(crateId);
        int taken = Math.min(current, Math.max(0, amount));
        addVirtualKeys(crateId, -taken);
        return taken;
    }

    public void setVirtualKeys(String crateId, int amount) {
        int safeAmount = Math.max(0, amount);
        if (safeAmount == 0) {
            virtualKeys.remove(crateId);
        } else {
            virtualKeys.put(crateId, safeAmount);
        }
    }

    public long cooldownUntil(String crateId) {
        return Math.max(0L, cooldownUntil.getOrDefault(crateId, 0L));
    }

    public void setCooldownUntil(String crateId, long timestamp) {
        if (timestamp <= 0L) {
            cooldownUntil.remove(crateId);
        } else {
            cooldownUntil.put(crateId, timestamp);
        }
    }

    public void resetCooldown(String crateId) {
        cooldownUntil.remove(crateId);
    }

    public void resetAllCooldowns() {
        cooldownUntil.clear();
    }

    public int openings(String crateId) {
        return Math.max(0, openings.getOrDefault(crateId, 0));
    }

    public int addOpening(String crateId) {
        int next = openings(crateId) + 1;
        openings.put(crateId, next);
        return next;
    }

    public void setOpenings(String crateId, int amount) {
        int safeAmount = Math.max(0, amount);
        if (safeAmount == 0) {
            openings.remove(crateId);
        } else {
            openings.put(crateId, safeAmount);
        }
    }

    public void resetOpenings(String crateId) {
        openings.remove(crateId);
    }

    public void resetAllOpenings() {
        openings.clear();
    }

    public int rewardClaims(String rewardKey) {
        return Math.max(0, rewardClaims.getOrDefault(rewardKey, 0));
    }

    public int addRewardClaim(String rewardKey) {
        int next = rewardClaims(rewardKey) + 1;
        rewardClaims.put(rewardKey, next);
        return next;
    }

    public void addRewardClaimTime(String rewardKey, long timestamp) {
        if (timestamp <= 0L) {
            return;
        }
        rewardClaimTimes.computeIfAbsent(rewardKey, ignored -> new ArrayList<>()).add(timestamp);
    }

    public int rewardClaimsSince(String rewardKey, long since) {
        List<Long> timestamps = rewardClaimTimes.get(rewardKey);
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

    public long rewardClaimWindowResetMillis(String rewardKey, long windowMillis, int limit, long now) {
        if (windowMillis <= 0L || limit <= 0) {
            return 0L;
        }
        List<Long> timestamps = rewardClaimTimes.get(rewardKey);
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

    public void resetRewardClaims(String rewardKey) {
        rewardClaims.remove(rewardKey);
        rewardClaimTimes.remove(rewardKey);
    }

    public void resetRewardClaimsForCrate(String crateId) {
        rewardClaims.keySet().removeIf(key -> key.startsWith(crateId + "."));
        rewardClaimTimes.keySet().removeIf(key -> key.startsWith(crateId + "."));
    }

    public void pruneRewardClaimTimes(long oldestAllowed) {
        rewardClaimTimes.values().forEach(timestamps -> timestamps.removeIf(timestamp -> timestamp < oldestAllowed));
        rewardClaimTimes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
