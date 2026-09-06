package gg.neonnexus.smpplatform.phase3.common;

import java.util.function.Supplier;

/**
 * Owns an all-or-nothing mutation boundary. Implementations must roll back on every thrown error;
 * callers must invoke it from an asynchronous executor rather than the Paper main thread.
 */
@FunctionalInterface
public interface TransactionRunner {
    <T> T required(Supplier<T> work);
}
