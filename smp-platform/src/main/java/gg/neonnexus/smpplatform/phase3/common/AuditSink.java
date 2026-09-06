package gg.neonnexus.smpplatform.phase3.common;

import gg.neonnexus.smpplatform.phase3.world.NeonWorld;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Implement with the Phase 2 audit repository; intentionally contains no secret or token fields. */
@FunctionalInterface
public interface AuditSink {
    AuditSink NOOP = entry -> { };
    void record(AuditEntry entry);

    record AuditEntry(UUID actorId, String action, String target, NeonWorld world, Map<String, String> metadata, Instant occurredAt, UUID correlationId) {
        public AuditEntry { metadata = Map.copyOf(metadata); }
    }
}
