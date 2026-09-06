package network.neonnexus.smp.admin.moderation;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class ModerationListener implements Listener {
    private final ModerationState state;
    public ModerationListener(ModerationState state) { this.state = state; }
    @EventHandler(ignoreCancelled = true) public void onMove(PlayerMoveEvent event) {
        if (!state.frozen(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom()); event.getPlayer().sendActionBar(Component.text("You are frozen by staff."));
        }
    }
    @EventHandler(ignoreCancelled = true) public void onChat(AsyncPlayerChatEvent event) {
        if (state.muted(event.getPlayer().getUniqueId())) { event.setCancelled(true); event.getPlayer().sendMessage(Component.text("You are currently muted.")); }
    }
}
