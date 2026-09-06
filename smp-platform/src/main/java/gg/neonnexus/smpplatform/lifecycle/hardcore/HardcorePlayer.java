package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.time.Instant;
import java.util.UUID;

/** Current state projection for one player in one Obsidian Gate season. */
public record HardcorePlayer(UUID playerId, String season, HardcoreState state, Instant stateChangedAt, Instant lifeStartedAt) { }
