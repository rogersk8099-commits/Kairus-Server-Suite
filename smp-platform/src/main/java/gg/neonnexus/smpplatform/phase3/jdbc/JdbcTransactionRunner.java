package gg.neonnexus.smpplatform.phase3.jdbc;

import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.common.TransactionRunner;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** Thread-bound JDBC transaction manager; configure with HikariDataSource in the parent bootstrap. */
public final class JdbcTransactionRunner implements TransactionRunner {
    private final DataSource dataSource;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();
    public JdbcTransactionRunner(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }
    public Connection connection() {
        Connection connection = current.get();
        if (connection == null) throw new IllegalStateException("No active database transaction on this thread");
        return connection;
    }
    @Override public <T> T required(Supplier<T> work) {
        Objects.requireNonNull(work);
        if (current.get() != null) return work.get();
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false); current.set(connection);
            try { T result = work.get(); connection.commit(); return result; }
            catch (RuntimeException exception) { rollback(connection); throw exception; }
            catch (Error error) { rollback(connection); throw error; }
            finally { current.remove(); connection.setAutoCommit(autoCommit); }
        } catch (SQLException exception) { throw new Phase3Exception(Phase3Exception.Code.FAILED_STORAGE, "Database transaction failed safely", exception); }
    }
    private static void rollback(Connection connection) { try { connection.rollback(); } catch (SQLException ignored) { } }
}
