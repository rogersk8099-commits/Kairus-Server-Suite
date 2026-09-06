package com.neonnexus.smpplatform.world;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record WorldRegistrySnapshot(
        long revision,
        Instant lastSyncAt,
        boolean offlineMode,
        String offlineReason,
        String lastConflict,
        Map<String, WorldDefinition> worlds
) {
    public WorldRegistrySnapshot {
        worlds = Map.copyOf(new LinkedHashMap<>(worlds));
    }

    public Optional<WorldDefinition> find(String id) {
        return Optional.ofNullable(worlds.get(id));
    }
}
