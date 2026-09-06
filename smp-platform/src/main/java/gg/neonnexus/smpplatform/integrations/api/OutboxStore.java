package gg.neonnexus.smpplatform.integrations.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {
    void enqueue(OutboxEvent event);
    List<OutboxEvent> claimDue(Instant now, int limit);
    void markDelivered(UUID id, Instant deliveredAt);
    void reschedule(UUID id, int attempts, Instant nextAttempt, String error);
}
