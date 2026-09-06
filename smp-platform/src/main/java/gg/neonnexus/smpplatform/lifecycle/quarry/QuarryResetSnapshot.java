package gg.neonnexus.smpplatform.lifecycle.quarry;

import java.time.Instant;

/** Persisted world_resets-style projection allowing staff to see exactly where a reset stopped. */
public record QuarryResetSnapshot(String correlationId, QuarryResetState state, Instant changedAt, String detail) { }
