package gg.neonnexus.smpplatform.integrations.skript;

import java.util.Optional;
import java.util.UUID;

/** Minimal synchronous cache/write-queue API exposed to Skript. Implementations must not perform HTTP or SQL inline. */
public interface NexusSkriptBridge {
    Optional<String> worldType(UUID playerUuid);
    Optional<String> guild(UUID playerUuid);
    long points(UUID playerUuid, String currency);
    boolean addPoints(UUID playerUuid, String currency, long amount, String reason);
    boolean removePoints(UUID playerUuid, String currency, long amount, String reason);
    Optional<String> hardcoreStatus(UUID playerUuid);
    Optional<String> membership(UUID playerUuid);
    boolean linked(UUID playerUuid);
    boolean sendNotification(UUID playerUuid, String message);
    boolean triggerPlatformEvent(UUID playerUuid, String eventType, String metadata);
    boolean createGuildEvent(UUID playerUuid, String eventName);
}
