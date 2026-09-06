package com.kairu.bridge.chat;

import java.util.ArrayDeque;
import java.util.Deque;

/** Small monotonic-clock sliding window limiter; safe for listener and scheduler callers. */
final class SlidingWindowRateLimiter {
    private final int limit;
    private final long windowNanos;
    private final Deque<Long> acceptedAt = new ArrayDeque<>();

    SlidingWindowRateLimiter(int limit, long windowMillis) {
        if (limit < 1 || windowMillis < 1) throw new IllegalArgumentException("Invalid rate limit");
        this.limit = limit;
        this.windowNanos = Math.multiplyExact(windowMillis, 1_000_000L);
    }

    synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        long threshold = now - windowNanos;
        while (!acceptedAt.isEmpty() && acceptedAt.peekFirst() <= threshold) acceptedAt.removeFirst();
        if (acceptedAt.size() >= limit) return false;
        acceptedAt.addLast(now);
        return true;
    }
}
