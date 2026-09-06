package com.neonnexus.smpplatform.async;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns all Phase 1 background execution; no database or HTTP tasks belong on the server thread. */
public final class PlatformExecutors implements AutoCloseable {
    private final ExecutorService io;
    private final ScheduledExecutorService scheduler;
    private final Duration shutdownTimeout;

    public PlatformExecutors(int ioThreads, Duration shutdownTimeout) {
        if (ioThreads < 1) throw new IllegalArgumentException("ioThreads must be positive");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout);
        this.io = Executors.newFixedThreadPool(ioThreads, namedFactory("SMPPlatform-IO-"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(namedFactory("SMPPlatform-Scheduler-"));
    }
    public ExecutorService io() { return io; }
    public ScheduledExecutorService scheduler() { return scheduler; }
    @Override public void close() {
        io.shutdown(); scheduler.shutdown();
        try {
            if (!io.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) io.shutdownNow();
            if (!scheduler.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException interrupted) {
            io.shutdownNow(); scheduler.shutdownNow(); Thread.currentThread().interrupt();
        }
    }
    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> { Thread thread = new Thread(runnable, prefix + counter.incrementAndGet()); thread.setDaemon(true); return thread; };
    }
}
