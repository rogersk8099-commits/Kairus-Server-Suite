package com.kairu.bridge.chat;

import com.kairu.bridge.KairuBridgePlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.advancement.Advancement;
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/** Paper/Purpur listener set. It does not cancel, mutate, or synchronize Minecraft chat events. */
public final class ChatBridgeListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final KairuBridgePlugin plugin;
    private final ChatBridgeService bridge;

    public ChatBridgeListener(KairuBridgePlugin plugin, ChatBridgeService bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    /** AsyncChatEvent may run off-thread. Capture only immutable values, then schedule Bukkit reads onto the primary thread. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String content = PLAIN.serialize(event.originalMessage());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) bridge.publishChat(player, content);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bridge.publishPlayerEvent(BridgeEventType.PLAYER_JOIN, player, player.getName() + " joined the server", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        bridge.publishPlayerEvent(BridgeEventType.PLAYER_LEAVE, player, player.getName() + " left the server", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        String message = event.deathMessage() == null ? player.getName() + " died" : PLAIN.serialize(event.deathMessage());
        bridge.publishPlayerEvent(BridgeEventType.PLAYER_DEATH, player, message, Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null || display.isHidden() || !display.doesAnnounceToChat()) return;
        Player player = event.getPlayer();
        String title = PLAIN.serialize(display.title());
        bridge.publishPlayerEvent(BridgeEventType.PLAYER_ADVANCEMENT, player,
                player.getName() + " made the advancement " + title,
                Map.of("advancementKey", advancement.getKey().asString(), "advancementTitle", title));
    }
}
