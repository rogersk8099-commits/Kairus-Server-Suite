package com.neonnexus.smp.eventscreative;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

enum PlatformDestination { WEBSITE, DISCORD }

enum PlatformEventType {
    EVENT_CREATED, EVENT_STATE_CHANGED, EVENT_RSVP_CHANGED, EVENT_CHECK_IN, EVENT_STARTED, EVENT_RESULT, EVENT_ARCHIVED,
    BUILD_SUBMITTED, BUILD_REVIEWED, BUILD_FEATURED, BUILD_REJECTED
}

record PlatformOutboxEvent(UUID id, String aggregateType, UUID aggregateId, PlatformEventType type,
                           Set<PlatformDestination> destinations, Map<String, Object> payload,
                           String idempotencyKey, int attempts, Instant availableAt) {
    PlatformOutboxEvent {
        Objects.requireNonNull(id, "id");
        if (aggregateType == null || aggregateType.isBlank()) throw new IllegalArgumentException("aggregateType required");
        Objects.requireNonNull(aggregateId, "aggregateId"); Objects.requireNonNull(type, "type");
        destinations = Set.copyOf(Objects.requireNonNull(destinations, "destinations"));
        if (destinations.isEmpty()) throw new IllegalArgumentException("At least one destination is required");
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey required");
        Objects.requireNonNull(availableAt, "availableAt");
    }
    PlatformOutboxEvent retryAt(Instant now) {
        int nextAttempts = attempts + 1;
        long seconds = Math.min(300, 5L * (1L << Math.min(5, attempts)));
        return new PlatformOutboxEvent(id, aggregateType, aggregateId, type, destinations, payload, idempotencyKey, nextAttempts, now.plusSeconds(seconds));
    }
}

interface OutboxRepository {
    void enqueue(PlatformOutboxEvent event);
    java.util.List<PlatformOutboxEvent> claimReady(Instant now, int limit);
    void markDelivered(UUID eventId, Instant deliveredAt);
    void reschedule(PlatformOutboxEvent event, String error);
}

/** Delivery is asynchronous. The plugin contains no Discord token; the Central API routes Discord events. */
interface PlatformEventPublisher {
    void deliver(PlatformOutboxEvent event) throws Exception;
}

final class OutboxDispatcher {
    private final OutboxRepository outbox;
    private final PlatformEventPublisher publisher;
    OutboxDispatcher(OutboxRepository outbox, PlatformEventPublisher publisher) { this.outbox = outbox; this.publisher = publisher; }
    void deliverReady(Instant now, int limit) {
        for (PlatformOutboxEvent event : outbox.claimReady(now, limit)) {
            try { publisher.deliver(event); outbox.markDelivered(event.id(), now); }
            catch (Exception failure) { outbox.reschedule(event.retryAt(now), safeMessage(failure)); }
        }
    }
    private static String safeMessage(Exception failure) { return failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "delivery failed"); }
}
