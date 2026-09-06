package com.neonnexus.smpplatform.outbox;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL implementation. All calls must be made by the configured IO executor. */
public final class JdbcOutboxRepository implements OutboxRepository {
    private final DataSource dataSource;
    public JdbcOutboxRepository(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override public EnqueueResult enqueue(OutboxEvent event) {
        String sql = "INSERT INTO smp_event_outbox (id,aggregate_type,aggregate_id,event_type,idempotency_key,payload,status,attempt_count,available_at,created_at,updated_at) VALUES (?,?,?,?,?,CAST(? AS jsonb),'PENDING',0,?,?,?) ON CONFLICT (idempotency_key) DO NOTHING";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.from(event.createdAt());
            statement.setObject(1, event.id()); statement.setString(2, event.aggregateType()); statement.setString(3, event.aggregateId()); statement.setString(4, event.eventType()); statement.setString(5, event.idempotencyKey()); statement.setString(6, event.payloadJson()); statement.setTimestamp(7, Timestamp.from(event.availableAt())); statement.setTimestamp(8, now); statement.setTimestamp(9, now);
            if (statement.executeUpdate() == 1) return new EnqueueResult(event, true);
            return new EnqueueResult(findByIdempotency(connection, event.idempotencyKey()), false);
        } catch (Exception exception) { throw new OutboxPersistenceException("Unable to enqueue outbox event", exception); }
    }

    @Override public List<OutboxEvent> claimDue(Instant now, Instant leaseUntil, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        String sql = "WITH candidates AS (SELECT id FROM smp_event_outbox WHERE (status='PENDING' AND available_at<=?) OR (status='LEASED' AND lease_until<?) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT ?) " +
                "UPDATE smp_event_outbox e SET status='LEASED', lease_until=?, updated_at=? FROM candidates c WHERE e.id=c.id RETURNING e.*";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.setTimestamp(1, Timestamp.from(now)); statement.setTimestamp(2, Timestamp.from(now)); statement.setInt(3, limit); statement.setTimestamp(4, Timestamp.from(leaseUntil)); statement.setTimestamp(5, Timestamp.from(now));
            List<OutboxEvent> claimed = new ArrayList<>();
            try (ResultSet results = statement.executeQuery()) { while (results.next()) claimed.add(read(results)); }
            connection.commit(); return claimed;
        } catch (Exception exception) { throw new OutboxPersistenceException("Unable to claim outbox smp_events", exception); }
    }

    @Override public void acknowledgeDelivered(OutboxEvent event, Instant deliveredAt) {
        update("UPDATE smp_event_outbox SET status='DELIVERED', delivered_at=?, lease_until=NULL, last_error=NULL, updated_at=? WHERE id=? AND status='LEASED'", event.id(), deliveredAt, deliveredAt, null, false);
    }
    @Override public void retry(OutboxEvent event, Instant nextAttemptAt, String error, boolean terminal) {
        String status = terminal ? "DEAD" : "PENDING";
        String sql = "UPDATE smp_event_outbox SET status=?, attempt_count=attempt_count+1, available_at=?, lease_until=NULL, last_error=?, updated_at=? WHERE id=? AND status='LEASED'";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) { s.setString(1, status); s.setTimestamp(2, Timestamp.from(nextAttemptAt)); s.setString(3, abbreviate(error)); s.setTimestamp(4, Timestamp.from(Instant.now())); s.setObject(5, event.id()); s.executeUpdate(); }
        catch (Exception exception) { throw new OutboxPersistenceException("Unable to reschedule outbox event", exception); }
    }
    private void update(String sql, UUID id, Instant first, Instant second, String error, boolean ignored) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) { s.setTimestamp(1, Timestamp.from(first)); s.setTimestamp(2, Timestamp.from(second)); s.setObject(3, id); s.executeUpdate(); }
        catch (Exception exception) { throw new OutboxPersistenceException("Unable to acknowledge outbox event", exception); }
    }
    private OutboxEvent findByIdempotency(Connection connection, String key) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM smp_event_outbox WHERE idempotency_key=?")) { statement.setString(1, key); try (ResultSet results = statement.executeQuery()) { if (!results.next()) throw new IllegalStateException("Outbox idempotency conflict row was not found"); return read(results); } }
    }
    private static OutboxEvent read(ResultSet results) throws Exception {
        Timestamp lease = results.getTimestamp("lease_until"), delivered = results.getTimestamp("delivered_at");
        return new OutboxEvent(results.getObject("id", UUID.class), results.getString("aggregate_type"), results.getString("aggregate_id"), results.getString("event_type"), results.getString("idempotency_key"), results.getString("payload"), OutboxEvent.Status.valueOf(results.getString("status")), results.getInt("attempt_count"), results.getTimestamp("available_at").toInstant(), lease == null ? null : lease.toInstant(), delivered == null ? null : delivered.toInstant(), results.getString("last_error"), results.getTimestamp("created_at").toInstant());
    }
    private static String abbreviate(String error) { if (error == null) return "Unknown delivery failure"; return error.length() <= 4000 ? error : error.substring(0, 4000); }
    public static final class OutboxPersistenceException extends RuntimeException { public OutboxPersistenceException(String message, Throwable cause) { super(message, cause); } }
}
