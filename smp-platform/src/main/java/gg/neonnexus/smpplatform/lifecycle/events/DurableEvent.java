package gg.neonnexus.smpplatform.lifecycle.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** An append-only platform event that may later be delivered by a central API outbox worker. */
public record DurableEvent(UUID id, String type, Instant occurredAt, String correlationId, Map<String, String> attributes) {
    public DurableEvent {
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
