package gg.neonnexus.smpplatform.integrations.messaging;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.util.Optional;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * World-aware chat publication observes Paper's async chat event and never blocks or modifies chat delivery.
 * Verdance remains disabled by default inside WorldChatBridge, and policy can disable other individual worlds.
 */
public final class PaperWorldChatListener implements Listener {
    private final WorldIdentityResolver identity; private final WorldChatBridge bridge; private final Clock clock;
    public PaperWorldChatListener(WorldIdentityResolver identity, WorldChatBridge bridge, Clock clock) { this.identity = identity; this.bridge = bridge; this.clock = clock; }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Optional<gg.neonnexus.smpplatform.integrations.NexusWorld> world = identity.worldForMinecraftWorld(event.getPlayer().getWorld().getName());
        if (world.isEmpty()) return;
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).strip();
        if (message.isEmpty() || message.length() > 256) return;
        WorldChatPayload payload = new WorldChatPayload(event.getPlayer().getUniqueId(), identity.platformUserId(event.getPlayer().getUniqueId()).orElse(null), event.getPlayer().getName(), world.get(), message, clock.instant());
        bridge.publish(payload).exceptionally(error -> false); // Explicitly fire-and-forget; chat must remain available during API outage.
    }
}
