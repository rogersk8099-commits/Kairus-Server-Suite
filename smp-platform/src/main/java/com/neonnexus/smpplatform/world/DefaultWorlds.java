package com.neonnexus.smpplatform.world;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/** The only built-in defaults. IDs and display names intentionally mirror neon-nexus-hub. */
public final class DefaultWorlds {
    public static final List<String> REQUIRED_IDS = List.of(
            "spawn-hub", "ashfall", "obsidian-gate", "atrium", "colosseum", "quarry", "verdance");

    private DefaultWorlds() { }

    public static RegistryDocument document() {
        return new RegistryDocument(1, Instant.EPOCH, definitions());
    }

    public static List<WorldDefinition> definitions() {
        return List.of(
                world("spawn-hub", "spawn-hub", "Spawn Hub",
                        "The permanent Kairu SMP entry point, with world navigation, player help, announcements and account linking.",
                        WorldType.HUB, "Permanent", WorldStatus.LIVE, "Peaceful", 0,
                        PvpMode.DISABLED, false, false, "HUB_POINTS", false, false, "HUB_GROUP",
                        new ResetPolicy.None(), ArchivePolicy.none(), true, true, true,
                        "smpplatform.world.spawn-hub"),
                world("ashfall", "ashfall", "Ashfall",
                        "The flagship survival map. Nation borders, claim wars and a fully player-run economy running on redstone freight lines.",
                        WorldType.SURVIVAL, "Season 7", WorldStatus.LIVE, "Hard", 24_000,
                        PvpMode.ENABLED, true, true, "NEXUS_POINTS", true, true, "ASHFALL_GROUP",
                        new ResetPolicy.None(), ArchivePolicy.none(), true, true, true,
                        "smpplatform.world.ashfall"),
                world("obsidian-gate", "obsidian-gate", "Obsidian Gate",
                        "One life. Shared world. Death moves you to spectator until the next reset window opens on Sunday night.",
                        WorldType.HARDCORE, "Season 7", WorldStatus.LIVE, "Brutal", 8_000,
                        PvpMode.ENABLED, true, true, "HARDCORE_POINTS", false, false, "HARDCORE_GROUP",
                        new ResetPolicy.Manual("Hardcore reset lifecycle is controlled by hardcore.yml"), ArchivePolicy.none(), true, true, true,
                        "smpplatform.world.obsidian-gate"),
                world("atrium", "atrium", "The Atrium",
                        "Plot-based creative sandbox with WorldEdit, custom palettes and monthly featured build showcases.",
                        WorldType.CREATIVE, "Permanent", WorldStatus.LIVE, "Peaceful", 0,
                        PvpMode.DISABLED, true, true, "BUILD_POINTS", false, false, "CREATIVE_GROUP",
                        new ResetPolicy.None(), ArchivePolicy.none(), true, true, true,
                        "smpplatform.world.atrium"),
                world("colosseum", "colosseum", "Neon Colosseum",
                        "Arena network for tournaments: Sky Duels, Block Rush and the Friday night 64-player Gauntlet.",
                        WorldType.EVENT, "Rotating", WorldStatus.LIVE, "Normal", 0,
                        PvpMode.EVENT_CONTROLLED, true, true, "EVENT_POINTS", false, false, "EVENT_GROUP",
                        new ResetPolicy.Manual("Event instances control their own reset"), ArchivePolicy.none(), true, true, true,
                        "smpplatform.world.colosseum"),
                world("quarry", "quarry", "The Quarry",
                        "Resource world that regenerates every hour with a fresh normal Overworld generation so the main map keeps its terrain intact.",
                        WorldType.RESOURCE, "Hourly Reset", WorldStatus.SEASONAL, "Normal", 12_000,
                        PvpMode.DISABLED, true, true, "NEXUS_POINTS", false, true, "ASHFALL_GROUP",
                        new ResetPolicy.Interval(java.time.Duration.ofHours(1), true), ArchivePolicy.none(), true, true, true,
                        "smpplatform.world.quarry"),
                world("verdance", "verdance", "Verdance",
                        "The Season 6 map, frozen and open for tours. Download the world file from the member portal.",
                        WorldType.ARCHIVE, "Season 6", WorldStatus.ARCHIVED, "Hard", 20_000,
                        PvpMode.DISABLED, false, false, "NEXUS_POINTS", false, false, "ARCHIVE_GROUP",
                        new ResetPolicy.None(), ArchivePolicy.strictTourMode(), false, true, true,
                        "smpplatform.world.verdance")
        );
    }

    private static WorldDefinition world(String id, String minecraftName, String displayName, String description,
                                         WorldType type, String season, WorldStatus status, String difficulty, long borderSize,
                                         PvpMode pvpMode, boolean guilds, boolean points, String currency, boolean claims,
                                         boolean economy, String inventoryGroup, ResetPolicy reset, ArchivePolicy archive,
                                         boolean discord, boolean websiteVisible, boolean mapVisible, String permission) {
        return new WorldDefinition(id, minecraftName, displayName, description, type, season, status, difficulty,
                borderSize, pvpMode, guilds, points, currency, claims, economy, inventoryGroup, reset, archive,
                discord, websiteVisible, mapVisible, 0, false, permission,
                new SpawnLocation(minecraftName, 0.5, 80.0, 0.5, 0f, 0f));
    }
}
