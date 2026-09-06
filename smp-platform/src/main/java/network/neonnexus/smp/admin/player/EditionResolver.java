package network.neonnexus.smp.admin.player;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/** Floodgate is optional. UUID identity is authoritative; player-name prefixes are never inspected. */
final class EditionResolver {
    private final Object floodgateApi;
    private final Method isFloodgatePlayer;

    EditionResolver(Plugin plugin) {
        Object api = null; Method method = null;
        if (plugin.getServer().getPluginManager().getPlugin("floodgate") != null) {
            try {
                Class<?> apiType = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                api = apiType.getMethod("getInstance").invoke(null);
                method = apiType.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException ignored) { /* optional adapter remains unavailable */ }
        }
        floodgateApi = api; isFloodgatePlayer = method;
    }

    PlayerDirectory.Edition resolve(Player player) {
        if (floodgateApi == null || isFloodgatePlayer == null) return PlayerDirectory.Edition.JAVA;
        try {
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(floodgateApi, player.getUniqueId()))
                    ? PlayerDirectory.Edition.BEDROCK : PlayerDirectory.Edition.JAVA;
        } catch (ReflectiveOperationException ignored) { return PlayerDirectory.Edition.UNKNOWN; }
    }
}
