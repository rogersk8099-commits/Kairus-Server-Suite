package com.neonnexus.smpplatform.identity;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/** Reflection keeps Floodgate optional and never uses username prefixes as an identity source. */
public final class FloodgateIdentityAdapter {
    private final Object floodgateApi;
    private final Method isFloodgatePlayer;
    private final Method getPlayer;
    private final Method getXuid;

    private FloodgateIdentityAdapter(Object api, Method isFloodgatePlayer, Method getPlayer, Method getXuid) {
        this.floodgateApi = api; this.isFloodgatePlayer = isFloodgatePlayer; this.getPlayer = getPlayer; this.getXuid = getXuid;
    }
    public static FloodgateIdentityAdapter discover(PluginManager plugins, Logger logger) {
        Objects.requireNonNull(plugins); Plugin plugin = plugins.getPlugin("floodgate");
        if (plugin == null || !plugin.isEnabled()) { logger.info("Floodgate not present; Java/unknown identity fallback is active."); return unavailable(); }
        try {
            Class<?> apiType = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, plugin.getClass().getClassLoader());
            Object api = apiType.getMethod("getInstance").invoke(null);
            Method present = apiType.getMethod("isFloodgatePlayer", java.util.UUID.class);
            Method player = apiType.getMethod("getPlayer", java.util.UUID.class);
            Class<?> floodgatePlayer = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer", true, plugin.getClass().getClassLoader());
            return new FloodgateIdentityAdapter(api, present, player, floodgatePlayer.getMethod("getXuid"));
        } catch (ReflectiveOperationException exception) { logger.warning("Floodgate detected but API is incompatible; using safe fallback: " + exception.getMessage()); return unavailable(); }
    }
    public PlayerIdentity resolve(Player player) {
        try {
            if (floodgateApi != null && (boolean) isFloodgatePlayer.invoke(floodgateApi, player.getUniqueId())) {
                Object floodgatePlayer = getPlayer.invoke(floodgateApi, player.getUniqueId());
                String xuid = floodgatePlayer == null ? null : (String) getXuid.invoke(floodgatePlayer);
                return new PlayerIdentity(player.getUniqueId(), player.getName(), PlayerIdentity.Edition.BEDROCK, xuid);
            }
        } catch (ReflectiveOperationException ignored) { /* safe Java fallback below */ }
        return new PlayerIdentity(player.getUniqueId(), player.getName(), PlayerIdentity.Edition.JAVA, null);
    }
    public boolean available() { return floodgateApi != null; }
    private static FloodgateIdentityAdapter unavailable() { return new FloodgateIdentityAdapter(null, null, null, null); }
}
