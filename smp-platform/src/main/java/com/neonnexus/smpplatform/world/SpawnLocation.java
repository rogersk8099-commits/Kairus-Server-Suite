package com.neonnexus.smpplatform.world;

import java.util.Objects;

public record SpawnLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
    public SpawnLocation {
        worldName = Objects.requireNonNull(worldName, "worldName").trim();
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("Spawn world name cannot be blank");
        }
    }
}
