package gg.neonnexus.smpplatform.phase3.world;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** World-specific capability policy supplied by the parent World Registry/configuration layer. */
public final class WorldPolicy {
    public enum GuildMode { DISABLED, FULL, CONFIGURABLE, SOCIAL, COMPETITION, INHERIT_ASHFALL, READ_ONLY }

    private final Map<NeonWorld, GuildMode> guildModes;
    private final boolean obsidianGateGuildsEnabled;

    public WorldPolicy(Map<NeonWorld, GuildMode> guildModes, boolean obsidianGateGuildsEnabled) {
        Objects.requireNonNull(guildModes, "guildModes");
        EnumMap<NeonWorld, GuildMode> normalized = new EnumMap<>(NeonWorld.class);
        normalized.putAll(defaultModes());
        normalized.putAll(guildModes);
        this.guildModes = Map.copyOf(normalized);
        this.obsidianGateGuildsEnabled = obsidianGateGuildsEnabled;
    }

    private static Map<NeonWorld, GuildMode> defaultModes() {
        return Map.of(
                NeonWorld.SPAWN_HUB, GuildMode.DISABLED,
                NeonWorld.ASHFALL, GuildMode.FULL,
                NeonWorld.OBSIDIAN_GATE, GuildMode.CONFIGURABLE,
                NeonWorld.ATRIUM, GuildMode.SOCIAL,
                NeonWorld.COLOSSEUM, GuildMode.COMPETITION,
                NeonWorld.QUARRY, GuildMode.INHERIT_ASHFALL,
                NeonWorld.VERDANCE, GuildMode.READ_ONLY
        );
    }

    public static WorldPolicy defaults() {
        return new WorldPolicy(Map.of(
                NeonWorld.SPAWN_HUB, GuildMode.DISABLED,
                NeonWorld.ASHFALL, GuildMode.FULL,
                NeonWorld.OBSIDIAN_GATE, GuildMode.CONFIGURABLE,
                NeonWorld.ATRIUM, GuildMode.SOCIAL,
                NeonWorld.COLOSSEUM, GuildMode.COMPETITION,
                NeonWorld.QUARRY, GuildMode.INHERIT_ASHFALL,
                NeonWorld.VERDANCE, GuildMode.READ_ONLY
        ), false);
    }

    public GuildMode guildMode(NeonWorld world) { return guildModes.get(Objects.requireNonNull(world, "world")); }

    /** Creates/membership mutations are intentionally denied in archived Verdance. */
    public boolean permitsGuildMutation(NeonWorld world) {
        return switch (guildMode(world)) {
            case DISABLED, READ_ONLY -> false;
            case CONFIGURABLE -> obsidianGateGuildsEnabled;
            case FULL, SOCIAL, COMPETITION, INHERIT_ASHFALL -> true;
        };
    }

    /** Ashfall's guild membership is the canonical membership used by the Quarry. */
    public boolean inheritsAshfallGuild(NeonWorld world) {
        return guildMode(world) == GuildMode.INHERIT_ASHFALL;
    }

    public boolean permitsCurrency(NeonWorld world, String currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
        if (world == NeonWorld.SPAWN_HUB) return false;
        if (world == NeonWorld.VERDANCE) return false;
        return switch (currencyId) {
            case "HARDCORE_POINTS" -> world == NeonWorld.OBSIDIAN_GATE;
            case "BUILD_POINTS" -> world == NeonWorld.ATRIUM;
            case "EVENT_POINTS" -> world == NeonWorld.COLOSSEUM;
            case "GUILD_POINTS" -> world != NeonWorld.ATRIUM && world != NeonWorld.VERDANCE;
            default -> true;
        };
    }
}
