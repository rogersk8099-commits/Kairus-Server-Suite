package com.neonnexus.smpplatform.outbox;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

class OutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Executor DIRECT = Runnable::run;
    @Test void dedupesByIdempotencyKeyBeforeDelivery() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        OutboxEvent event = event("join:player-1:001");
        assertTrue(repository.enqueue(event).inserted());
        assertFalse(repository.enqueue(event).inserted());
        assertEquals(event.id(), repository.find(event.idempotencyKey()).id());
        assertEquals(1, repository.claimDue(NOW, NOW.plusSeconds(60), 10).size());
    }
    @Test void rejectedDeliveryIsRetriedWithExponentialBackoff() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        OutboxEvent event = event("death:player-1:001"); repository.enqueue(event);
        OutboxDispatcher dispatcher = new OutboxDispatcher(repository, ignored -> java.util.concurrent.CompletableFuture.completedFuture(new OutboxDeliveryClient.DeliveryReceipt(false, "offline")), DIRECT, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(5), 3, java.util.logging.Logger.getAnonymousLogger());
        dispatcher.dispatchOnce(10).join();
        OutboxEvent retried = repository.find(event.idempotencyKey());
        assertEquals(OutboxEvent.Status.PENDING, retried.status()); assertEquals(1, retried.attemptCount()); assertEquals(NOW.plusSeconds(5), retried.availableAt());
        assertEquals(Duration.ofSeconds(10), dispatcher.backoff(2));
    }
    @Test void acknowledgementMarksEventDeliveredAndPreventsAnotherClaim() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository(); OutboxEvent event = event("points:player-1:001"); repository.enqueue(event);
        OutboxDispatcher dispatcher = new OutboxDispatcher(repository, ignored -> java.util.concurrent.CompletableFuture.completedFuture(OutboxDeliveryClient.DeliveryReceipt.acknowledged()), DIRECT, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(5), 3, java.util.logging.Logger.getAnonymousLogger());
        dispatcher.dispatchOnce(10).join();
        assertEquals(OutboxEvent.Status.DELIVERED, repository.find(event.idempotencyKey()).status()); assertTrue(repository.claimDue(NOW.plusSeconds(300), NOW.plusSeconds(360), 10).isEmpty());
    }
    private static OutboxEvent event(String key) { return OutboxEvent.pending("player", "player-1", "PLAYER_JOINED", key, "{\"player\":\"player-1\"}", NOW); }
}
