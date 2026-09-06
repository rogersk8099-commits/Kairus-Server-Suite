package com.neonnexus.smpplatform.world;

import java.util.Objects;

/** Immutable Minecraft-side representation of one Neon Nexus platform world. */
public record WorldDefinition(
        String id,
        String minecraftWorldName,
        String displayName,
        String description,
        WorldType type,
        String season,
        WorldStatus status,
        String difficulty,
        long borderSize,
        PvpMode pvpMode,
        boolean guildsEnabled,
        boolean pointsEnabled,
        String currencyId,
        boolean claimsEnabled,
        boolean economyEnabled,
        String inventoryGroup,
        ResetPolicy resetPolicy,
        ArchivePolicy archivePolicy,
        boolean discordEnabled,
        boolean websiteVisible,
        boolean mapVisible,
        int playerCount,
        boolean maintenanceMode,
        String accessPermission,
        SpawnLocation spawnLocation
) {
    public WorldDefinition {
        id = identifier(id, "id");
        minecraftWorldName = identifier(minecraftWorldName, "minecraftWorldName");
        displayName = required(displayName, "displayName");
        description = required(description, "description");
        type = Objects.requireNonNull(type, "type");
        season = required(season, "season");
        status = Objects.requireNonNull(status, "status");
        difficulty = required(difficulty, "difficulty");
        if (borderSize < 0) throw new IllegalArgumentException("borderSize cannot be negative");
        pvpMode = Objects.requireNonNull(pvpMode, "pvpMode");
        currencyId = required(currencyId, "currencyId");
        inventoryGroup = required(inventoryGroup, "inventoryGroup");
        resetPolicy = Objects.requireNonNull(resetPolicy, "resetPolicy");
        archivePolicy = Objects.requireNonNull(archivePolicy, "archivePolicy");
        if (playerCount < 0) throw new IllegalArgumentException("playerCount cannot be negative");
        accessPermission = required(accessPermission, "accessPermission");
        spawnLocation = Objects.requireNonNull(spawnLocation, "spawnLocation");
    }

    public boolean isAvailableForPlayers() {
        return !maintenanceMode && status != WorldStatus.OFFLINE;
    }

    private static String identifier(String value, String field) {
        value = required(value, field).toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("[a-z][a-z0-9_.-]{1,62}")) {
            throw new IllegalArgumentException(field + " must be a stable lowercase identifier");
        }
        return value;
    }

    private static String required(String value, String field) {
        value = Objects.requireNonNull(value, field).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }
}
