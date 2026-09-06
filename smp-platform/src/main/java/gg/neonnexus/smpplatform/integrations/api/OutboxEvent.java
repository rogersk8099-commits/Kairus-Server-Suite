package gg.neonnexus.smpplatform.integrations.api;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(UUID id, CentralApiOperation operation, String eventType, String payloadJson, int attempts,
                          Instant availableAt, Instant createdAt) {
    public OutboxEvent {
        if (id == null || operation == null || eventType == null || payloadJson == null || availableAt == null || createdAt == null) throw new IllegalArgumentException("Outbox fields are required");
        if (payloadJson.length() > 65536) throw new IllegalArgumentException("Outbox payload exceeds 64 KiB");
    }
}
