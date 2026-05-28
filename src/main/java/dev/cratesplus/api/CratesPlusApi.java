package dev.cratesplus.api;

import dev.cratesplus.crate.CrateDefinition;
import dev.cratesplus.crate.CrateService;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public final class CratesPlusApi {
    private static CrateService service;

    private CratesPlusApi() {
    }

    public static void register(CrateService crateService) {
        service = crateService;
    }

    public static void unregister() {
        service = null;
    }

    public static boolean available() {
        return service != null;
    }

    public static Collection<CrateDefinition> crates() {
        ensureAvailable();
        return service.crates();
    }

    public static Optional<CrateDefinition> crate(String crateId) {
        ensureAvailable();
        return Optional.ofNullable(service.crate(crateId));
    }

    public static int virtualKeys(UUID playerUuid, String crateId) {
        ensureAvailable();
        return service.virtualKeys(playerUuid, crateId);
    }

    private static void ensureAvailable() {
        if (service == null) {
            throw new IllegalStateException("CratesPlus API is not available");
        }
    }
}
