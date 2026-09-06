package gg.neonnexus.smpplatform.integrations.placeholder;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import java.util.Locale;
import java.util.UUID;

/** Resolver for all documented %nexus_*% values. Unsupported requests return null as PlaceholderAPI expects. */
public final class NexusPlaceholders {
    private final NexusPlaceholderValues values;
    public NexusPlaceholders(NexusPlaceholderValues values) { this.values = values; }
    public String resolve(UUID playerId, String parameter) {
        if (playerId == null || parameter == null) return "";
        return switch (parameter.toLowerCase(Locale.ROOT)) {
            case "world" -> values.world(playerId).map(NexusWorld::id).orElse("unknown");
            case "world_name" -> values.world(playerId).map(NexusWorld::displayName).orElse("Unknown");
            case "guild" -> blank(values.guild(playerId), "None");
            case "guild_tag" -> blank(values.guildTag(playerId), "-");
            case "points" -> Long.toString(values.points(playerId, "NEXUS_POINTS"));
            case "hardcore_status" -> blank(values.hardcoreStatus(playerId), "ALIVE");
            case "membership" -> blank(values.membership(playerId), "Initiate");
            case "platform" -> blank(values.platform(playerId), "JAVA");
            case "season" -> blank(values.season(playerId), values.world(playerId).map(NexusWorld::season).orElse(""));
            case "event_points" -> Long.toString(values.eventPoints(playerId));
            default -> null;
        };
    }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
