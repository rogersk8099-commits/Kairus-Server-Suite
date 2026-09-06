package gg.neonnexus.smpplatform.integrations.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** PostgreSQL-backed outbox. claimDue takes a durable 60-second lease with SKIP LOCKED before delivery. */
public final class JdbcOutboxStore implements OutboxStore {
    private final DataSource dataSource;
    public JdbcOutboxStore(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }
    @Override public void enqueue(OutboxEvent event) {
        String sql = "INSERT INTO smp_event_outbox (id, operation, event_type, payload_json, attempts, available_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            bind(p, event); p.executeUpdate();
        } catch (SQLException e) { throw new OutboxPersistenceException(e); }
    }
    @Override public List<OutboxEvent> claimDue(Instant now, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        // A committed lease closes the gap that would exist if row locks were released before HTTP completed.
        Instant leaseUntil = now.plusSeconds(60);
        String sql = "WITH due AS (SELECT id FROM smp_event_outbox WHERE delivered_at IS NULL AND available_at <= ? ORDER BY available_at, created_at FOR UPDATE SKIP LOCKED LIMIT ?) "
            + "UPDATE smp_event_outbox row SET available_at = ? FROM due WHERE row.id = due.id "
            + "RETURNING row.id, row.operation, row.event_type, row.payload_json::text AS payload_json, row.attempts, row.available_at, row.created_at";
        List<OutboxEvent> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setTimestamp(1, Timestamp.from(now)); p.setInt(2, limit); p.setTimestamp(3, Timestamp.from(leaseUntil));
            try (ResultSet rs = p.executeQuery()) { while (rs.next()) result.add(read(rs)); }
            return List.copyOf(result);
        } catch (SQLException e) { throw new OutboxPersistenceException(e); }
    }
    @Override public void markDelivered(UUID id, Instant deliveredAt) { update("UPDATE smp_event_outbox SET delivered_at = ?, last_error = NULL WHERE id = ?", deliveredAt, id, null, null); }
    @Override public void reschedule(UUID id, int attempts, Instant nextAttempt, String error) { update("UPDATE smp_event_outbox SET attempts = ?, available_at = ?, last_error = ? WHERE id = ?", nextAttempt, id, attempts, error); }
    private void update(String sql, Instant firstInstant, UUID id, Integer attempts, String error) {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            if (attempts == null) { p.setTimestamp(1, Timestamp.from(firstInstant)); p.setObject(2, id); }
            else { p.setInt(1, attempts); p.setTimestamp(2, Timestamp.from(firstInstant)); p.setString(3, truncate(error)); p.setObject(4, id); }
            p.executeUpdate();
        } catch (SQLException e) { throw new OutboxPersistenceException(e); }
    }
    private static void bind(PreparedStatement p, OutboxEvent event) throws SQLException {
        p.setObject(1, event.id()); p.setString(2, event.operation().name()); p.setString(3, event.eventType()); p.setString(4, event.payloadJson()); p.setInt(5, event.attempts()); p.setTimestamp(6, Timestamp.from(event.availableAt())); p.setTimestamp(7, Timestamp.from(event.createdAt()));
    }
    private static OutboxEvent read(ResultSet rs) throws SQLException { return new OutboxEvent(rs.getObject("id", UUID.class), CentralApiOperation.valueOf(rs.getString("operation")), rs.getString("event_type"), rs.getString("payload_json"), rs.getInt("attempts"), rs.getTimestamp("available_at").toInstant(), rs.getTimestamp("created_at").toInstant()); }
    private static String truncate(String input) { return input == null ? null : input.substring(0, Math.min(input.length(), 1000)); }
    public static final class OutboxPersistenceException extends RuntimeException { public OutboxPersistenceException(SQLException cause) { super("Outbox persistence failure", cause); } }
}
