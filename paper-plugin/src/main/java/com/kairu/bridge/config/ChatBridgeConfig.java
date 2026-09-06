package com.kairu.bridge.config;

import com.kairu.bridge.chat.BridgeEventType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validated settings for the optional chat and presence bridge. This configuration does not contain
 * Discord credentials: the authenticated control plane remains the only Discord-facing component.
 */
public record ChatBridgeConfig(
        boolean minecraftToDiscordEnabled,
        boolean discordToMinecraftEnabled,
        int outgoingMaxCharacters,
        int incomingMaxCharacters,
        int maxPayloadBytes,
        int outboundMessagesPerWindow,
        long outboundWindowMillis,
        int inboundPollLimit,
        long inboundPollIntervalTicks,
        long duplicateTtlMillis,
        List<String> enabledWorlds,
        String inboundTargetWorld,
        boolean joinEventsEnabled,
        boolean leaveEventsEnabled,
        boolean deathEventsEnabled,
        boolean advancementEventsEnabled,
        boolean lifecycleEnabled,
        boolean startupNotificationEnabled,
        boolean shutdownNotificationEnabled,
        boolean maintenanceNotificationEnabled
) {
    private static final long MIN_POLL_SECONDS = 5L;

    public ChatBridgeConfig {
        enabledWorlds = enabledWorlds == null ? List.of() : List.copyOf(enabledWorlds.stream()
                .filter(Objects::nonNull).map(ChatBridgeConfig::worldKey).filter(value -> !value.isBlank()).distinct().toList());
        inboundTargetWorld = inboundTargetWorld == null ? "" : inboundTargetWorld.strip();
    }

    public static ChatBridgeConfig from(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new ChatBridgeConfig(
                config.getBoolean("chat-bridge.minecraft-to-discord.enabled", true),
                config.getBoolean("chat-bridge.discord-to-minecraft.enabled", true),
                bounded(config.getInt("chat-bridge.minecraft-to-discord.max-characters", 240), 1, 500),
                bounded(config.getInt("chat-bridge.discord-to-minecraft.max-characters", 240), 1, 500),
                bounded(config.getInt("chat-bridge.max-payload-bytes", 8_192), 1_024, 16_384),
                bounded(config.getInt("chat-bridge.minecraft-to-discord.rate-limit.messages", 20), 1, 120),
                bounded(config.getLong("chat-bridge.minecraft-to-discord.rate-limit.window-seconds", 10), 1, 60) * 1_000L,
                bounded(config.getInt("chat-bridge.discord-to-minecraft.poll-limit", 20), 1, 50),
                bounded(config.getLong("chat-bridge.discord-to-minecraft.poll-interval-seconds", 5), MIN_POLL_SECONDS, 60) * 20L,
                bounded(config.getLong("chat-bridge.discord-to-minecraft.duplicate-ttl-seconds", 600), 60, 3_600) * 1_000L,
                config.getStringList("chat-bridge.worlds.enabled"),
                boundedWorld(config.getString("chat-bridge.discord-to-minecraft.target-world", "")),
                config.getBoolean("chat-bridge.events.join", true),
                config.getBoolean("chat-bridge.events.leave", true),
                config.getBoolean("chat-bridge.events.death", true),
                config.getBoolean("chat-bridge.events.advancement", true),
                config.getBoolean("chat-bridge.lifecycle.enabled", true),
                config.getBoolean("chat-bridge.lifecycle.startup-notification", true),
                config.getBoolean("chat-bridge.lifecycle.shutdown-notification", true),
                config.getBoolean("chat-bridge.lifecycle.maintenance-notification", true)
        );
    }

    /** An empty enabled-world list deliberately means every loaded world is eligible. */
    public boolean worldEnabled(String worldName) {
        return worldName != null && (enabledWorlds.isEmpty() || enabledWorlds.contains(worldKey(worldName)));
    }

    /** A fixed local target wins over a target requested by the queued control-plane message. */
    public String resolveInboundTarget(String queuedTargetWorld) {
        if (!inboundTargetWorld.isBlank()) return inboundTargetWorld;
        return queuedTargetWorld == null ? "" : queuedTargetWorld.strip();
    }

    public boolean allows(BridgeEventType eventType) {
        return switch (Objects.requireNonNull(eventType, "eventType")) {
            case CHAT -> minecraftToDiscordEnabled;
            case PLAYER_JOIN -> joinEventsEnabled;
            case PLAYER_LEAVE -> leaveEventsEnabled;
            case PLAYER_DEATH -> deathEventsEnabled;
            case PLAYER_ADVANCEMENT -> advancementEventsEnabled;
            case SERVER_STARTED -> lifecycleEnabled && startupNotificationEnabled;
            case SERVER_STOPPING -> lifecycleEnabled && shutdownNotificationEnabled;
            case MAINTENANCE -> lifecycleEnabled && maintenanceNotificationEnabled;
        };
    }

    private static int bounded(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static long bounded(long value, long minimum, long maximum) { return Math.max(minimum, Math.min(maximum, value)); }

    private static String boundedWorld(String value) {
        String world = value == null ? "" : value.strip();
        if (world.codePointCount(0, world.length()) > 128 || containsControl(world)) {
            throw new IllegalArgumentException("chat-bridge discord-to-minecraft target-world is invalid");
        }
        return world;
    }

    private static String worldKey(String world) { return world.strip().toLowerCase(Locale.ROOT); }
    private static boolean containsControl(String text) {
        return text.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT);
    }
}
