package com.neonnexus.smpplatform.outbox;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository {
    /** Returns the pre-existing event when idempotency_key is already present. */
    EnqueueResult enqueue(OutboxEvent event);
    /** Atomically leases due events so concurrent workers cannot deliver the same row. */
    List<OutboxEvent> claimDue(Instant now, Instant leaseUntil, int limit);
    void acknowledgeDelivered(OutboxEvent event, Instant deliveredAt);
    void retry(OutboxEvent event, Instant nextAttemptAt, String error, boolean terminal);

    record EnqueueResult(OutboxEvent event, boolean inserted) { }
}
