package gg.neonnexus.smpplatform.lifecycle.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable staff-action record. Repositories must persist this outside the game thread. */
public record AuditEntry(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        String action,
        String target,
        String worldId,
        String beforeState,
        String afterState,
        String reason,
        String correlationId,
        Map<String, String> metadata
) {
    public AuditEntry {
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
