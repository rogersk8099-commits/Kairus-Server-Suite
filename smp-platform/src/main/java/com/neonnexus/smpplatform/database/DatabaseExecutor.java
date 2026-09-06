package com.neonnexus.smpplatform.database;

import org.bukkit.Bukkit;
import java.sql.Connection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Requires callers to use the configured IO executor; refuses direct Paper-main-thread database work. */
public final class DatabaseExecutor {
    private final DatabaseService database;
    private final Executor io;
    public DatabaseExecutor(DatabaseService database, Executor io) { this.database = Objects.requireNonNull(database); this.io = Objects.requireNonNull(io); }

    public <T> CompletableFuture<T> transaction(Function<Connection, T> work) {
        Objects.requireNonNull(work);
        if (Bukkit.isPrimaryThread()) throw new IllegalStateException("SQL may not be scheduled from the Paper main thread; use async service boundaries");
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.requireDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try { T result = work.apply(connection); connection.commit(); return result; }
                catch (RuntimeException failure) { connection.rollback(); throw failure; }
            } catch (Exception failure) { throw new DatabaseOperationException("Database transaction failed", failure); }
        }, io);
    }
    public static final class DatabaseOperationException extends RuntimeException { public DatabaseOperationException(String message, Throwable cause) { super(message, cause); } }
}
