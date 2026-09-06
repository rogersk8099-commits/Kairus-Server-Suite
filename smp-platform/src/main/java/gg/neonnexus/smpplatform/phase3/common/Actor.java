package gg.neonnexus.smpplatform.phase3.common;

import gg.neonnexus.smpplatform.phase3.world.NeonWorld;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A resolved Minecraft identity; Floodgate identity resolution belongs to the parent identity layer. */
public record Actor(UUID playerId, String displayName, NeonWorld world, Set<String> permissions) {
    public Actor {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(world, "world");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public boolean has(String permission) { return permissions.contains("*") || permissions.contains(permission); }
    public boolean isGuildAdmin() { return has("smpplatform.admin") || has("smpplatform.admin.guilds") || has("smpplatform.guild.manage"); }
    public boolean isPointsAdmin() { return has("smpplatform.admin") || has("smpplatform.admin.points"); }
}
