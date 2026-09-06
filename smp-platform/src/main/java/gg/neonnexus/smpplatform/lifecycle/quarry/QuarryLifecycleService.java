package gg.neonnexus.smpplatform.lifecycle.quarry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.UUID;

/** Starts the blocking safety coordinator on a dedicated worker, never Paper's primary thread. */
public final class QuarryLifecycleService {
    private final QuarryResetCoordinator coordinator;
    private final Executor executor;
    public QuarryLifecycleService(QuarryResetCoordinator coordinator, Executor executor) { this.coordinator = coordinator; this.executor = executor; }
    public CompletableFuture<QuarryResetSnapshot> resetAsync(String correlationId) {
        return CompletableFuture.supplyAsync(() -> coordinator.execute(correlationId), executor);
    }
    public CompletableFuture<QuarryResetSnapshot> scheduledResetAsync() { return resetAsync(UUID.randomUUID().toString()); }
}
