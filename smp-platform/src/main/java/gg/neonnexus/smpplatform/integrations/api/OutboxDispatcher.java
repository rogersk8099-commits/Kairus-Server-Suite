package gg.neonnexus.smpplatform.integrations.api;

import gg.neonnexus.smpplatform.integrations.IntegrationHealth;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Runs database work and delivery on ioExecutor. At-least-once delivery is made safe by the outbox UUID idempotency key. */
public final class OutboxDispatcher {
    private final OutboxStore store; private final CentralApiClient client; private final Executor ioExecutor; private final IntegrationHealth health;
    public OutboxDispatcher(OutboxStore store, CentralApiClient client, Executor ioExecutor, IntegrationHealth health) { this.store = Objects.requireNonNull(store); this.client = Objects.requireNonNull(client); this.ioExecutor = Objects.requireNonNull(ioExecutor); this.health = Objects.requireNonNull(health); }
    public CompletableFuture<Integer> dispatchOnceAsync(int batchSize) {
        return CompletableFuture.supplyAsync(() -> store.claimDue(Instant.now(), batchSize), ioExecutor)
            .thenCompose(events -> CompletableFuture.allOf(events.stream().map(this::deliver).toArray(CompletableFuture[]::new)).thenApply(ignored -> events.size()));
    }
    private CompletableFuture<Void> deliver(OutboxEvent event) {
        return client.sendJson(event.operation(), event.payloadJson(), event.id())
            .thenAcceptAsync(response -> {
                if (response.successful()) store.markDelivered(event.id(), Instant.now());
                else retry(event, "HTTP " + response.statusCode());
            }, ioExecutor)
            .exceptionally(error -> { retry(event, error.getClass().getSimpleName()); return null; });
    }
    private void retry(OutboxEvent event, String error) {
        int attempts = event.attempts() + 1;
        long seconds = Math.min(3600L, 5L * (1L << Math.min(10, attempts - 1)));
        store.reschedule(event.id(), attempts, Instant.now().plus(Duration.ofSeconds(seconds)), error);
        health.report("Central API", IntegrationHealth.State.WARNING, "Outbox retry " + attempts + ": " + error);
    }
}
