package com.kairu.bridge.chat;

import com.kairu.bridge.KairuBridgePlugin;
import com.kairu.bridge.api.ControlPlaneClient;
import com.kairu.bridge.config.ChatBridgeConfig;
import com.kairu.bridge.payload.BridgeEventPayload;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Coordinates bridge event publishing and queued Discord-to-Minecraft delivery. Bukkit reads and
 * broadcasts are confined to the primary thread; the transport uses ControlPlaneClient async HTTP
 * and its existing retry model. The central API owns queue state and Discord delivery.
 */
public final class ChatBridgeService {
    private static final String INBOUND_PREFIX = "§9[Discord] §r";

    private final KairuBridgePlugin plugin;
    private final ChatBridgeConfig config;
    private final ChatBridgeTransport transport;
    private final SlidingWindowRateLimiter outboundLimiter;
    private final RecentMessageIds recentInboundIds;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean pollInFlight = new AtomicBoolean();
    private volatile BukkitTask inboundPollTask;

    public ChatBridgeService(KairuBridgePlugin plugin, ControlPlaneClient client, ChatBridgeConfig config) {
        this(plugin, config, new ControlPlaneChatTransport(client));
    }

    /* Package-visible constructor makes direction, payload, and main-thread delivery behavior unit-testable. */
    ChatBridgeService(KairuBridgePlugin plugin, ChatBridgeConfig config, ChatBridgeTransport transport) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.outboundLimiter = new SlidingWindowRateLimiter(config.outboundMessagesPerWindow(), config.outboundWindowMillis());
        this.recentInboundIds = new RecentMessageIds(config.duplicateTtlMillis());
    }

    /** Must be called on the Bukkit primary thread as part of plugin lifecycle installation. */
    public void start() {
        requirePrimaryThread();
        if (stopped.get() || !config.discordToMinecraftEnabled()) return;
        inboundPollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollQueuedMessages,
                config.inboundPollIntervalTicks(), config.inboundPollIntervalTicks());
    }

    /** Safe during plugin shutdown; queued messages remain in the central API if they were not acknowledged. */
    public void stop() {
        stopped.set(true);
        BukkitTask task = inboundPollTask;
        inboundPollTask = null;
        if (task != null) task.cancel();
    }

    /** Primary-thread only: reads player/world state synchronously and immediately returns after async dispatch. */
    public void publishChat(Player player, String rawMessage) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player");
        if (!config.minecraftToDiscordEnabled() || !config.worldEnabled(player.getWorld().getName()) || LoopMarkers.containsBridgeMarker(rawMessage)) return;
        String content = ChatSanitizer.sanitize(LoopMarkers.removeBridgeMarkers(rawMessage), config.outgoingMaxCharacters());
        if (content.isBlank()) return;
        publish(BridgeEventType.CHAT, player.getWorld().getName(), player.getUniqueId(), player.getName(), content, Map.of());
    }

    /** Primary-thread only: captures the Bukkit event/player details before HTTP work begins. */
    public void publishPlayerEvent(BridgeEventType type, Player player, String content, Map<String, String> details) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player");
        if (!config.allows(type) || !config.worldEnabled(player.getWorld().getName())) return;
        publish(type, player.getWorld().getName(), player.getUniqueId(), player.getName(), content, details);
    }

    /** Main-thread lifecycle signal; maintenance callers can use this before a planned restart. */
    public void publishLifecycle(BridgeEventType type, String content, Map<String, String> details) {
        requirePrimaryThread();
        if (!config.allows(type)) return;
        publish(type, "", null, "", content, details);
    }

    /** Convenience API for an administrative maintenance/restart command or integration. */
    public void notifyMaintenance(String message, boolean restarting) {
        publishLifecycle(BridgeEventType.MAINTENANCE, message, Map.of("restarting", Boolean.toString(restarting)));
    }

    private void publish(BridgeEventType type, String worldName, UUID minecraftUuid, String minecraftName, String content, Map<String, String> details) {
        if (stopped.get() || !outboundLimiter.tryAcquire()) {
            if (!stopped.get()) plugin.getLogger().fine("KairuBridge dropped " + type + " due to the configured outbound rate limit.");
            return;
        }
        BridgeEventPayload payload = BridgeEventPayload.event(type, worldName, minecraftUuid, minecraftName,
                ChatSanitizer.sanitize(content, config.outgoingMaxCharacters()), details);
        transport.publish(payload, config.maxPayloadBytes()).exceptionally(error -> {
            if (!stopped.get()) plugin.getLogger().log(Level.FINE, "KairuBridge bridge event delivery failed: " + type, error);
            return null;
        });
    }

    private void pollQueuedMessages() {
        if (stopped.get() || !config.discordToMinecraftEnabled() || !pollInFlight.compareAndSet(false, true)) return;
        transport.poll(config.inboundPollLimit()).whenComplete((messages, error) -> {
            pollInFlight.set(false);
            if (stopped.get()) return;
            if (error != null) {
                plugin.getLogger().log(Level.FINE, "KairuBridge queued Discord chat poll failed", error);
                return;
            }
            if (messages != null && !messages.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> deliverQueuedOnPrimary(messages));
        });
    }

    /** Called only by Bukkit's synchronous scheduler. No thread touches worlds/players off-thread. */
    private void deliverQueuedOnPrimary(List<QueuedDiscordMessage> messages) {
        requirePrimaryThread();
        if (stopped.get() || !config.discordToMinecraftEnabled()) return;
        for (QueuedDiscordMessage queued : messages) deliverOneOnPrimary(queued);
    }

    private void deliverOneOnPrimary(QueuedDiscordMessage queued) {
        if (recentInboundIds.alreadyDelivered(queued.id())) {
            acknowledge(queued.id(), "delivered", "duplicate already delivered");
            return;
        }
        String targetName = config.resolveInboundTarget(queued.targetWorld());
        World target = targetName.isBlank() ? null : Bukkit.getWorld(targetName);
        if (!targetName.isBlank() && target == null) {
            acknowledge(queued.id(), "rejected", "target world is not loaded");
            return;
        }
        String displayName = ChatSanitizer.sanitize(queued.displayName(), 48);
        String content = ChatSanitizer.sanitize(queued.content(), config.incomingMaxCharacters());
        if (content.isBlank()) {
            acknowledge(queued.id(), "rejected", "message has no visible content");
            return;
        }
        String visible = INBOUND_PREFIX + (displayName.isBlank() ? "Discord" : displayName) + "§7: §r" + content;
        String marked = LoopMarkers.markDiscordOrigin(visible, queued.id());
        if (target == null) Bukkit.broadcastMessage(marked);
        else target.getPlayers().forEach(player -> player.sendMessage(marked));
        recentInboundIds.markDelivered(queued.id());
        acknowledge(queued.id(), "delivered", "broadcast to Minecraft");
    }

    private void acknowledge(String id, String status, String detail) {
        transport.acknowledge(id, status, detail).exceptionally(error -> {
            if (!stopped.get()) plugin.getLogger().log(Level.FINE, "KairuBridge queued chat acknowledgement failed", error);
            return null;
        });
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bukkit operation invoked off the primary thread");
    }
}
