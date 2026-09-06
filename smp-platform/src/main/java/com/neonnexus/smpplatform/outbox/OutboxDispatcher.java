package com.neonnexus.smpplatform.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Drains the durable outbox off-thread. A delivery is never marked delivered without an acknowledgement. */
public final class OutboxDispatcher {
    private final OutboxRepository repository; private final OutboxDeliveryClient client; private final Executor io;
    private final Clock clock; private final Duration baseBackoff; private final int maxAttempts; private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean();
    public OutboxDispatcher(OutboxRepository repository, OutboxDeliveryClient client, Executor io, Clock clock, Duration baseBackoff, int maxAttempts, Logger logger) {
        this.repository = Objects.requireNonNull(repository); this.client = Objects.requireNonNull(client); this.io = Objects.requireNonNull(io); this.clock = Objects.requireNonNull(clock); this.baseBackoff = Objects.requireNonNull(baseBackoff); this.maxAttempts = maxAttempts; this.logger = Objects.requireNonNull(logger);
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
    }
    public CompletableFuture<Void> dispatchOnce(int batchSize) {
        if (!running.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        Instant now = clock.instant();
        return CompletableFuture.supplyAsync(() -> repository.claimDue(now, now.plus(Duration.ofMinutes(2)), batchSize), io)
                .thenCompose(events -> CompletableFuture.allOf(events.stream().map(this::deliver).toArray(CompletableFuture[]::new)))
                .whenComplete((ignored, failure) -> { running.set(false); if (failure != null) logger.warning("Outbox dispatch cycle failed: " + failure.getMessage()); });
    }
    private CompletableFuture<Void> deliver(OutboxEvent event) {
        try {
            return client.deliver(event).thenAcceptAsync(receipt -> { if (receipt.accepted()) repository.acknowledgeDelivered(event, clock.instant()); else failure(event, "Remote rejected event: " + receipt.detail()); }, io).exceptionally(failure -> { failure(event, failure.getMessage()); return null; }).toCompletableFuture();
        } catch (RuntimeException failure) { failure(event, failure.getMessage()); return CompletableFuture.completedFuture(null); }
    }
    private void failure(OutboxEvent event, String error) {
        int nextAttempt = event.attemptCount() + 1; boolean terminal = nextAttempt >= maxAttempts;
        repository.retry(event, clock.instant().plus(backoff(nextAttempt)), error, terminal);
        if (terminal) logger.severe("Outbox event " + event.id() + " reached max attempts and is DEAD: " + error);
    }
    Duration backoff(int attempt) { long multiplier = 1L << Math.min(20, Math.max(0, attempt - 1)); return baseBackoff.multipliedBy(multiplier).compareTo(Duration.ofHours(6)) > 0 ? Duration.ofHours(6) : baseBackoff.multipliedBy(multiplier); }
}
