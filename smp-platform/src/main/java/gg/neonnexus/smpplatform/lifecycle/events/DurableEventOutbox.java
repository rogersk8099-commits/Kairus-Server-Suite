package gg.neonnexus.smpplatform.lifecycle.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Durable boundary for event_outbox. A PostgreSQL implementation can atomically append together
 * with lifecycle mutations; the file implementation provides an outage-safe local fallback.
 */
public interface DurableEventOutbox {
    void publish(DurableEvent event);

    default void publish(String type, String correlationId, Map<String, String> attributes) {
        publish(new DurableEvent(UUID.randomUUID(), type, Instant.now(), correlationId, attributes));
    }
}
