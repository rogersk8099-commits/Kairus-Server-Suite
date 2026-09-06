package com.neonnexus.smpplatform.world;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class WorldRegistrySynchronizer {
    private final WorldRegistry registry;
    private final WorldRegistryRemoteSource source;
    private final Executor ioExecutor;
    private final Clock clock;
    private final Logger logger;
    private final AtomicBoolean inFlight = new AtomicBoolean();

    public WorldRegistrySynchronizer(WorldRegistry registry, WorldRegistryRemoteSource source, Executor ioExecutor, Clock clock, Logger logger) {
        this.registry = Objects.requireNonNull(registry); this.source = Objects.requireNonNull(source);
        this.ioExecutor = Objects.requireNonNull(ioExecutor); this.clock = Objects.requireNonNull(clock); this.logger = Objects.requireNonNull(logger);
    }

    public CompletableFuture<RegistryApplyResult> sync() {
        if (!inFlight.compareAndSet(false, true)) return CompletableFuture.failedFuture(new IllegalStateException("World Registry sync already running"));
        long known = registry.snapshot().revision();
        try {
            return source.fetch(known).thenApplyAsync(document -> registry.applyRemote(document, clock.instant()), ioExecutor)
                    .whenComplete((result, throwable) -> {
                        inFlight.set(false);
                        if (throwable != null) {
                            registry.setOffline("Central API World Registry sync failed: " + throwable.getMessage());
                            logger.warning("World Registry sync failed; retaining cached configuration: " + throwable.getMessage());
                        }
                    }).toCompletableFuture();
        } catch (RuntimeException exception) {
            inFlight.set(false);
            registry.setOffline("Central API World Registry sync could not start: " + exception.getMessage());
            return CompletableFuture.failedFuture(exception);
        }
    }
}
