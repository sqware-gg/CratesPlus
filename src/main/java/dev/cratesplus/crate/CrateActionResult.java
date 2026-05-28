package dev.cratesplus.crate;

import java.util.List;
import java.util.Map;

public record CrateActionResult(
        boolean success,
        String messageKey,
        Map<String, String> placeholders,
        CrateDefinition crate,
        List<CrateReward> rewards
) {
    public static CrateActionResult failure(String messageKey, Map<String, String> placeholders) {
        return new CrateActionResult(false, messageKey, Map.copyOf(placeholders), null, List.of());
    }

    public static CrateActionResult success(String messageKey, Map<String, String> placeholders,
                                            CrateDefinition crate, List<CrateReward> rewards) {
        return new CrateActionResult(true, messageKey, Map.copyOf(placeholders), crate, List.copyOf(rewards));
    }

    public static CrateActionResult info(String messageKey, Map<String, String> placeholders) {
        return new CrateActionResult(true, messageKey, Map.copyOf(placeholders), null, List.of());
    }
}
