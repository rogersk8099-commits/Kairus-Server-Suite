package com.neonnexus.smp.eventscreative;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable arena metadata. Runtime arena allocation/clone creation belongs behind ArenaRuntimeAdapter. */
public record ArenaDefinition(
    UUID id,
    String name,
    String worldId,
    EventType gameType,
    List<SpawnPoint> spawnPoints,
    SpawnPoint spectatorSpawn,
    int minimumPlayers,
    int maximumPlayers,
    CuboidBounds bounds,
    String resetPolicy,
    boolean instanced
) {
    public ArenaDefinition {
        Objects.requireNonNull(id, "id");
        requireText(name, "name");
        CanonicalWorlds.require(worldId, CanonicalWorlds.COLOSSEUM);
        Objects.requireNonNull(gameType, "gameType");
        spawnPoints = List.copyOf(Objects.requireNonNull(spawnPoints, "spawnPoints"));
        Objects.requireNonNull(spectatorSpawn, "spectatorSpawn");
        Objects.requireNonNull(bounds, "bounds");
        requireText(resetPolicy, "resetPolicy");
        if (minimumPlayers < 1 || maximumPlayers < minimumPlayers) throw new IllegalArgumentException("Invalid arena player range");
        if (spawnPoints.size() < minimumPlayers) throw new IllegalArgumentException("Arena has fewer spawn points than its minimum players");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public record SpawnPoint(double x, double y, double z, float yaw, float pitch) { }
    public record CuboidBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public CuboidBounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Bounds minima must not exceed maxima");
        }
    }
}
