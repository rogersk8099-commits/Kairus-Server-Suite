package com.neonnexus.smpplatform.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test double that follows the same idempotency and lease semantics as the JDBC repository. */
public final class InMemoryOutboxRepository implements OutboxRepository {
    private final Map<String, OutboxEvent> byKey = new LinkedHashMap<>();
    @Override public synchronized EnqueueResult enqueue(OutboxEvent event) { OutboxEvent old = byKey.get(event.idempotencyKey()); if (old != null) return new EnqueueResult(old, false); byKey.put(event.idempotencyKey(), event); return new EnqueueResult(event, true); }
    @Override public synchronized List<OutboxEvent> claimDue(Instant now, Instant leaseUntil, int limit) {
        List<OutboxEvent> claimed = new ArrayList<>();
        byKey.entrySet().stream().filter(entry -> claimable(entry.getValue(), now)).sorted(Comparator.comparing(entry -> entry.getValue().createdAt())).limit(limit).forEach(entry -> {
            OutboxEvent event = entry.getValue(); OutboxEvent leased = new OutboxEvent(event.id(), event.aggregateType(), event.aggregateId(), event.eventType(), event.idempotencyKey(), event.payloadJson(), OutboxEvent.Status.LEASED, event.attemptCount(), event.availableAt(), leaseUntil, event.deliveredAt(), event.lastError(), event.createdAt()); entry.setValue(leased); claimed.add(leased);
        }); return List.copyOf(claimed);
    }
    @Override public synchronized void acknowledgeDelivered(OutboxEvent event, Instant at) { replace(event.idempotencyKey(), new OutboxEvent(event.id(), event.aggregateType(), event.aggregateId(), event.eventType(), event.idempotencyKey(), event.payloadJson(), OutboxEvent.Status.DELIVERED, event.attemptCount(), event.availableAt(), null, at, null, event.createdAt())); }
    @Override public synchronized void retry(OutboxEvent event, Instant next, String error, boolean terminal) { replace(event.idempotencyKey(), new OutboxEvent(event.id(), event.aggregateType(), event.aggregateId(), event.eventType(), event.idempotencyKey(), event.payloadJson(), terminal ? OutboxEvent.Status.DEAD : OutboxEvent.Status.PENDING, event.attemptCount() + 1, next, null, event.deliveredAt(), error, event.createdAt())); }
    public synchronized OutboxEvent find(String key) { return byKey.get(key); }
    private void replace(String key, OutboxEvent replacement) { if (!byKey.containsKey(key)) throw new IllegalArgumentException("Unknown event " + key); byKey.put(key, replacement); }
    private boolean claimable(OutboxEvent event, Instant now) { return (event.status() == OutboxEvent.Status.PENDING && !event.availableAt().isAfter(now)) || (event.status() == OutboxEvent.Status.LEASED && event.leaseUntil() != null && event.leaseUntil().isBefore(now)); }
}
