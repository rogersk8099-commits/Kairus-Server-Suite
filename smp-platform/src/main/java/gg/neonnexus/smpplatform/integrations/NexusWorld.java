package gg.neonnexus.smpplatform.integrations;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The immutable public world vocabulary shared by Minecraft, the platform API, and Discord. */
public enum NexusWorld {
    SPAWN_HUB("spawn-hub", "Spawn Hub", "HUB", "Permanent", "Live"),
    ASHFALL("ashfall", "Ashfall", "SURVIVAL", "Season 7", "Live"),
    OBSIDIAN_GATE("obsidian-gate", "Obsidian Gate", "HARDCORE", "Season 7", "Live"),
    ATRIUM("atrium", "The Atrium", "CREATIVE", "Permanent", "Live"),
    COLOSSEUM("colosseum", "Neon Colosseum", "EVENT", "Rotating", "Live"),
    QUARRY("quarry", "The Quarry", "RESOURCE", "Hourly reset", "Seasonal"),
    VERDANCE("verdance", "Verdance", "ARCHIVE", "Season 6", "Archived");

    private final String id;
    private final String displayName;
    private final String type;
    private final String season;
    private final String status;

    NexusWorld(String id, String displayName, String type, String season, String status) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.season = season;
        this.status = status;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public String type() { return type; }
    public String season() { return season; }
    public String status() { return status; }
    public static Optional<NexusWorld> fromId(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(world -> world.id.equals(normalized)).findFirst();
    }
}
