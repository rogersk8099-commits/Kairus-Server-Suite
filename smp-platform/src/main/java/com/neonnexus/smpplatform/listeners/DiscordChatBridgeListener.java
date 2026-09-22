package com.neonnexus.smpplatform.listeners;

import com.neonnexus.smpplatform.SMPPlatform;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Publishes ordinary Minecraft chat without changing Paper's local delivery. */
public final class DiscordChatBridgeListener implements Listener {
    private final SMPPlatform plugin;
    public DiscordChatBridgeListener(SMPPlatform plugin) { this.plugin = plugin; }
    @EventHandler(ignoreCancelled = true) public void onChat(AsyncChatEvent event) {
        String content = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (!content.isBlank()) plugin.publishMinecraftChat(event.getPlayer(), content, "GLOBAL");
    }
}
