package com.kairu.bridge;

import com.kairu.bridge.integration.SoftIntegrations;
import com.kairu.bridge.payload.Payloads;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/** Reads Bukkit state only on the server thread; callers send the resulting immutable JSON asynchronously. */
public final class TelemetryService {
    private final long startedAtMillis;
    private final String pluginVersion;
    private final SoftIntegrations integrations;

    public TelemetryService(String serverId, String pluginVersion, SoftIntegrations integrations) {
        this.startedAtMillis = System.currentTimeMillis(); this.pluginVersion = pluginVersion; this.integrations = integrations;
    }

    public Payloads.ServerHeartbeat heartbeat() {
        double[] tps = Bukkit.getServer().getTPS();
        double currentTps = tps.length == 0 ? 0D : Math.max(0D, Math.min(20D, tps[0]));
        List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).toList();
        List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return new Payloads.ServerHeartbeat(currentTps, players.size(), worlds, players,
                "Paper " + Bukkit.getMinecraftVersion() + " / KairuBridge " + pluginVersion,
                Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1000L));
    }

    public Payloads.PlayerSnapshot snapshot(Player player) {
        long playtimeSeconds = statistic(player, Statistic.PLAY_ONE_MINUTE) / 20L;
        double distanceMeters = travelDistance(player) / 100.0D;
        Double balance = integrations.balance(player);
        String rank = integrations.rank(player);
        return new Payloads.PlayerSnapshot(player.getUniqueId(), player.getName(), playtimeSeconds,
                totalBlocksMined(player), statistic(player, Statistic.PLAYER_KILLS), statistic(player, Statistic.DEATHS), distanceMeters,
                balance == null ? 0D : balance, rank == null || rank.isBlank() ? "Member" : rank, player.getWorld().getName());
    }

    private static long totalBlocksMined(Player player) {
        long total = 0L;
        for (Material material : Material.values()) {
            if (!material.isBlock()) continue;
            try { total = Math.addExact(total, player.getStatistic(Statistic.MINE_BLOCK, material)); }
            catch (IllegalArgumentException | ArithmeticException ignored) { }
        }
        return total;
    }

    private static long travelDistance(Player player) {
        Statistic[] statistics = {Statistic.WALK_ONE_CM, Statistic.SPRINT_ONE_CM, Statistic.CROUCH_ONE_CM, Statistic.SWIM_ONE_CM, Statistic.FLY_ONE_CM,
                Statistic.CLIMB_ONE_CM, Statistic.MINECART_ONE_CM, Statistic.BOAT_ONE_CM, Statistic.PIG_ONE_CM, Statistic.HORSE_ONE_CM,
                Statistic.AVIATE_ONE_CM};
        long total = 0L;
        for (Statistic statistic : statistics) { try { total = Math.addExact(total, player.getStatistic(statistic)); } catch (IllegalArgumentException | ArithmeticException ignored) { } }
        return total;
    }

    private static long statistic(Player player, Statistic statistic) { try { return player.getStatistic(statistic); } catch (IllegalArgumentException ignored) { return 0L; } }
}
