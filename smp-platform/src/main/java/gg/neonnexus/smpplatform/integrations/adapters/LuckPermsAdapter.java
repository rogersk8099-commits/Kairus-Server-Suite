package gg.neonnexus.smpplatform.integrations.adapters;

import java.util.UUID;

/** Permission checks fall back to Bukkit's effective permissions when LuckPerms is absent. */
public final class LuckPermsAdapter {
    @FunctionalInterface public interface PermissionChecker { boolean has(UUID playerId, String permission); }
    private final SoftDependency luckPerms;
    private final PermissionChecker bukkitFallback;
    public LuckPermsAdapter(SoftDependency luckPerms, PermissionChecker bukkitFallback) {
        this.luckPerms = luckPerms; this.bukkitFallback = bukkitFallback;
    }
    public AdapterResult<Boolean> hasPermission(UUID playerId, String permission) {
        // Effective Bukkit permissions include LuckPerms attachments without linking its API.
        boolean allowed = bukkitFallback.has(playerId, permission);
        return luckPerms.available() ? AdapterResult.of(allowed) : new AdapterResult<>(false, java.util.Optional.of(allowed), "Bukkit permission fallback");
    }
}
