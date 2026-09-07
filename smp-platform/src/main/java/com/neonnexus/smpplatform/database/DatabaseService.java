package com.neonnexus.smpplatform.database;

import com.neonnexus.smpplatform.config.PlatformConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/** PostgreSQL is authoritative for durable platform gameplay data; SQL must run via PlatformExecutors. */
public final class DatabaseService implements AutoCloseable {
    public enum Health { STARTING, HEALTHY, UNAVAILABLE, CLOSED }
    private final PlatformConfiguration.Core.Database config;
    private final Logger logger;
    private final AtomicReference<Health> health = new AtomicReference<>(Health.STARTING);
    private HikariDataSource dataSource;

    public DatabaseService(PlatformConfiguration.Core.Database config, Logger logger) {
        this.config = Objects.requireNonNull(config); this.logger = Objects.requireNonNull(logger);
    }

    public void start() {
        DatabasePasswordResolver.Resolved secret;
        try {
            secret = DatabasePasswordResolver.resolve(config.passwordEnvironment(), config.passwordFile());
        } catch (RuntimeException exception) {
            health.set(Health.UNAVAILABLE);
            logger.severe("Database password could not be loaded safely; durable mutations are disabled: " + exception.getMessage());
            return;
        }
        if (secret.source() == DatabasePasswordResolver.Source.FILE) logger.info("Database password loaded from the protected SMPPlatform data-file fallback.");
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(config.jdbcUrl()); pool.setUsername(config.username()); pool.setPassword(secret.value());
        // Shadow relocates the JDBC driver; name it explicitly because JDBC service descriptors are not class-relocated reliably.
        pool.setDriverClassName("com.neonnexus.smpplatform.lib.postgresql.Driver");
        pool.setMaximumPoolSize(config.poolSize()); pool.setConnectionTimeout(config.connectionTimeout().toMillis());
        pool.setValidationTimeout(config.validationTimeout().toMillis()); pool.setPoolName("SMPPlatform-PostgreSQL");
        pool.setAutoCommit(true); pool.setInitializationFailTimeout(-1);
        try {
            dataSource = new HikariDataSource(pool);
            // Paper isolates plugin resources; scanning must use this plugin's classloader rather than the server context loader.
            Flyway.configure(DatabaseService.class.getClassLoader()).dataSource(dataSource).locations("classpath:db/migration").baselineOnMigrate(true).load().migrate();
            try (Connection ignored = dataSource.getConnection()) { health.set(Health.HEALTHY); }
        } catch (RuntimeException | SQLException exception) {
            health.set(Health.UNAVAILABLE);
            logger.severe("PostgreSQL migration/connection failed; durable mutations are disabled: " + exception.getMessage());
            close();
        }
    }

    public boolean isAvailable() { return health.get() == Health.HEALTHY; }
    public Health health() { return health.get(); }
    public DataSource requireDataSource() {
        if (!isAvailable() || dataSource == null) throw new IllegalStateException("PostgreSQL is unavailable; unsafe mutation refused");
        return dataSource;
    }
    public boolean reconnect() { if (health.get() == Health.CLOSED) return false; closePoolOnly(); start(); return isAvailable(); }
    @Override public void close() { closePoolOnly(); health.set(Health.CLOSED); }
    private void closePoolOnly() { if (dataSource != null) { dataSource.close(); dataSource = null; } }
}
