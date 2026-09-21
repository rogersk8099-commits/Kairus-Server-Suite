package com.neonnexus.smpplatform.listeners;

import com.neonnexus.smpplatform.SMPPlatform;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Map;
import java.util.Objects;

/** Captures ordinary player lifecycle events on Paper's thread and forwards only safe metadata to the Control Plane. */
public final class ControlPlaneBridgeListener implements Listener {
    private final SMPPlatform plugin;
    public ControlPlaneBridgeListener(SMPPlatform plugin) { this.plugin = Objects.requireNonNull(plugin); }
    @EventHandler public void join(PlayerJoinEvent event) { publish("PLAYER_JOIN", event.getPlayer(), event.getPlayer().getName() + " joined the server", Map.of()); }
    @EventHandler public void leave(PlayerQuitEvent event) { publish("PLAYER_LEAVE", event.getPlayer(), event.getPlayer().getName() + " left the server", Map.of()); }
    @EventHandler public void death(PlayerDeathEvent event) { Player player = event.getPlayer(); publish("PLAYER_DEATH", player, event.deathMessage() == null ? player.getName() + " died" : String.valueOf(event.deathMessage()), Map.of("cause", player.getLastDamageCause() == null ? "unknown" : player.getLastDamageCause().getCause().name())); }
    private void publish(String type, Player player, String content, Map<String, String> details) { plugin.publishBridgeEvent(type, player.getWorld().getName(), player, content, details); }
}
