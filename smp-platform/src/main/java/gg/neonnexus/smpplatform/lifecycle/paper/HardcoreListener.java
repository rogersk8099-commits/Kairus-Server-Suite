package gg.neonnexus.smpplatform.lifecycle.paper;

import gg.neonnexus.smpplatform.lifecycle.hardcore.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Captures death evidence and re-enforces spectator state on login, respawn, world changes, and gamemode attempts. */
public final class HardcoreListener implements Listener {
    private final HardcoreLifecycleService lifecycle;
    private final String worldName;
    private final String season;
    public HardcoreListener(HardcoreLifecycleService lifecycle, String worldName, String season) { this.lifecycle = lifecycle; this.worldName = worldName; this.season = season; }
    @EventHandler public void join(PlayerJoinEvent event) { lifecycle.registerIfAbsent(event.getPlayer().getUniqueId(), season, Instant.now()); enforce(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) {
        Player player = event.getEntity(); if (!worldName.equals(player.getWorld().getName())) return;
        Location at = player.getLocation(); Player killer = player.getKiller();
        Instant now = Instant.now();
        HardcorePlayer current = lifecycle.state(player.getUniqueId(), season).orElseGet(() -> lifecycle.registerIfAbsent(player.getUniqueId(), season, now));
        HardcoreStatistics known = snapshot(player);
        lifecycle.recordDeath(new DeathCapture(player.getUniqueId(), season, event.getDeathMessage() == null ? "unknown" : event.getDeathMessage(),
                killer == null ? null : killer.getUniqueId(), killer == null ? null : killer.getName(),
                new WorldPosition(worldName, at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch()), Duration.between(current.lifeStartedAt(), now), known, now), UUID.randomUUID().toString());
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) { if (lifecycle.state(event.getPlayer().getUniqueId(), season).map(HardcorePlayer::state).orElse(HardcoreState.ALIVE) == HardcoreState.SPECTATING) event.getPlayer().setGameMode(GameMode.SPECTATOR); }
    @EventHandler public void worldChange(PlayerChangedWorldEvent event) { enforce(event.getPlayer()); }
    @EventHandler(ignoreCancelled = true) public void gameMode(PlayerGameModeChangeEvent event) {
        if (lifecycle.state(event.getPlayer().getUniqueId(), season).map(HardcorePlayer::state).orElse(HardcoreState.ALIVE) == HardcoreState.SPECTATING && event.getNewGameMode() != GameMode.SPECTATOR) event.setCancelled(true);
    }
    private HardcoreStatistics snapshot(Player player) {
        long mined = java.util.Arrays.stream(org.bukkit.Material.values()).filter(org.bukkit.Material::isBlock)
                .mapToLong(material -> player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, material)).sum();
        long distance = (long) player.getStatistic(org.bukkit.Statistic.WALK_ONE_CM)
                + player.getStatistic(org.bukkit.Statistic.SPRINT_ONE_CM) + player.getStatistic(org.bukkit.Statistic.CROUCH_ONE_CM)
                + player.getStatistic(org.bukkit.Statistic.SWIM_ONE_CM) + player.getStatistic(org.bukkit.Statistic.FLY_ONE_CM);
        long bosses = player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.ENDER_DRAGON)
                + player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.WITHER)
                + player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.WARDEN);
        long seconds = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L;
        return new HardcoreStatistics(seconds, seconds / 86_400L, Math.max(0, player.getStatistic(org.bukkit.Statistic.DEATHS) - 1L),
                player.getStatistic(org.bukkit.Statistic.MOB_KILLS), player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS), bosses,
                distance, mined, 0L, 0L);
    }
    private void enforce(Player player) { if (worldName.equals(player.getWorld().getName()) && lifecycle.state(player.getUniqueId(), season).map(HardcorePlayer::state).orElse(HardcoreState.ALIVE) == HardcoreState.SPECTATING) player.setGameMode(GameMode.SPECTATOR); }
}
