package gg.neonnexus.smpplatform.integrations.messaging;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import java.time.Instant;
import java.util.UUID;

/** Canonical chat bridge message. User text is bounded before it enters storage or the network. */
public record WorldChatPayload(UUID minecraftUuid, String platformUserId, String playerName, NexusWorld world, String message, Instant timestamp) {
    public WorldChatPayload {
        if (minecraftUuid == null || world == null || timestamp == null) throw new IllegalArgumentException("Identity, world and timestamp are required");
        playerName = bounded(playerName, 16, "playerName"); message = bounded(message, 256, "message");
        if (platformUserId != null && platformUserId.length() > 128) throw new IllegalArgumentException("platformUserId is too long");
    }
    public boolean bridgeEnabledByDefault() { return world != NexusWorld.VERDANCE; }
    private static String bounded(String value, int maximum, String field) { if (value == null || value.isBlank() || value.length() > maximum) throw new IllegalArgumentException(field + " must be 1.." + maximum + " characters"); return value; }
}
