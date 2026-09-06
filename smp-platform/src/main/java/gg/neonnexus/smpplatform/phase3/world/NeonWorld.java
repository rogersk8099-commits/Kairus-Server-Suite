package gg.neonnexus.smpplatform.phase3.world;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The immutable, website-aligned default world registry keys. These identifiers and display names
 * are platform contracts; Minecraft runtime world names are deliberately resolved by the parent
 * World Registry instead of being duplicated here.
 */
public enum NeonWorld {
    ASHFALL("ashfall", "Ashfall", WorldKind.SURVIVAL),
    OBSIDIAN_GATE("obsidian-gate", "Obsidian Gate", WorldKind.HARDCORE),
    ATRIUM("atrium", "The Atrium", WorldKind.CREATIVE),
    COLOSSEUM("colosseum", "Neon Colosseum", WorldKind.EVENT),
    QUARRY("quarry", "The Quarry", WorldKind.RESOURCE),
    VERDANCE("verdance", "Verdance", WorldKind.ARCHIVE);

    private final String id;
    private final String displayName;
    private final WorldKind kind;

    NeonWorld(String id, String displayName, WorldKind kind) {
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public WorldKind kind() { return kind; }

    public static Optional<NeonWorld> fromId(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(world -> world.id.equals(normalized)).findFirst();
    }

    public enum WorldKind { SURVIVAL, HARDCORE, CREATIVE, EVENT, RESOURCE, ARCHIVE }
}
