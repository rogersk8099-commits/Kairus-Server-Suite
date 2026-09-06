package gg.neonnexus.smpplatform.lifecycle.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Blocks ordinary teleports and immediately ejects rare movement that arrives after The Quarry is locked. */
public final class QuarryEntryListener implements Listener {
    private final PaperQuarryGateway gateway;
    private final String quarryWorld;
    private final String fallbackWorld;
    public QuarryEntryListener(PaperQuarryGateway gateway, String quarryWorld, String fallbackWorld) { this.gateway = gateway; this.quarryWorld = quarryWorld; this.fallbackWorld = fallbackWorld; }
    @EventHandler(ignoreCancelled = true) public void teleport(PlayerTeleportEvent event) {
        if (gateway.isEntryLocked() && event.getTo() != null && event.getTo().getWorld() != null && quarryWorld.equals(event.getTo().getWorld().getName())) event.setCancelled(true);
    }
    @EventHandler public void worldChange(PlayerChangedWorldEvent event) {
        if (gateway.isEntryLocked() && quarryWorld.equals(event.getPlayer().getWorld().getName()) && event.getPlayer().getServer().getWorld(fallbackWorld) != null)
            event.getPlayer().teleport(event.getPlayer().getServer().getWorld(fallbackWorld).getSpawnLocation());
    }
}
