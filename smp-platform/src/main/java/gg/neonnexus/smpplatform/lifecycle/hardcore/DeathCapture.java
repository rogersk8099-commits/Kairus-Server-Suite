package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Input adapter from a Paper death event. Null killer values explicitly mean environmental death. */
public record DeathCapture(
        UUID playerId, String season, String cause, UUID killerId, String killerName,
        WorldPosition position, Duration survivalTime, HardcoreStatistics statistics, Instant occurredAt
) { }
