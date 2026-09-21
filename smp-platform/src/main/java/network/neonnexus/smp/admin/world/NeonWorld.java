package network.neonnexus.smp.admin.world;

import java.util.Arrays;
import java.util.Optional;

/**
 * Phase-2 view of the central World Registry. IDs/display names intentionally
 * mirror the website product specification and must not be casually renamed.
 */
public enum NeonWorld {
    SPAWN_HUB("spawn-hub", "Spawn Hub", "Hub", "Permanent", "Live", "Permanent", "Peaceful", "Protected central entry point with navigation and live server information."),
    ASHFALL("ashfall", "Ashfall", "Survival", "Season 7", "Live", "24k x 24k", "Hard", "Flagship survival: nations, claims and freight lines."),
    OBSIDIAN_GATE("obsidian-gate", "Obsidian Gate", "Hardcore", "Season 7", "Live", "8k x 8k", "Brutal", "One life; spectator until the reset window."),
    ATRIUM("atrium", "The Atrium", "Creative", "Permanent", "Live", "Plots 128x128", "Peaceful", "Permanent creative plots and featured builds."),
    COLOSSEUM("colosseum", "Neon Colosseum", "Events", "Rotating", "Live", "Instanced", "Normal", "Tournaments, duels, Block Rush and Gauntlet."),
    QUARRY("quarry", "The Quarry", "Resource", "Weekly reset", "Seasonal", "12k x 12k", "Normal", "Resource gathering; resets Monday 04:00 UTC."),
    VERDANCE("verdance", "Verdance", "Survival / Archive", "Season 6", "Archived", "20k x 20k", "Hard", "Frozen archive open for tours.");

    private final String id, displayName, type, season, status, size, difficulty, description;
    NeonWorld(String id, String displayName, String type, String season, String status, String size, String difficulty, String description) {
        this.id = id; this.displayName = displayName; this.type = type; this.season = season; this.status = status;
        this.size = size; this.difficulty = difficulty; this.description = description;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public String type() { return type; }
    public String season() { return season; }
    public String status() { return status; }
    public String size() { return size; }
    public String difficulty() { return difficulty; }
    public String description() { return description; }
    public static Optional<NeonWorld> byId(String id) { return Arrays.stream(values()).filter(w -> w.id.equalsIgnoreCase(id)).findFirst(); }
}
