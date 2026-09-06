package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Immutable evidence captured before the player is forced into spectator mode. */
public record HardcoreDeathRecord(
        UUID id, UUID playerId, String season, String cause, UUID killerId, String killerName,
        WorldPosition position, Duration survivalTime, HardcoreStatistics statistics, Instant occurredAt
) { }
