package gg.neonnexus.smpplatform.integrations.link;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** PostgreSQL implementation; consumption is one UPDATE ... RETURNING statement so a code can only succeed once. */
public final class JdbcLinkRepository implements LinkRepository {
    private final DataSource dataSource;
    public JdbcLinkRepository(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public long countGeneratedSince(UUID uuid, Instant since) { return scalar("SELECT count(*) FROM smp_account_links WHERE minecraft_uuid = ? AND created_at >= ?", uuid, since); }
    @Override public void create(LinkRecord r) { execute("INSERT INTO smp_account_links (id,minecraft_uuid,code_hash,expires_at,created_at) VALUES (?,?,?,?,now())", r.id(), r.minecraftUuid(), r.codeHash(), r.expiresAt()); }
    @Override public Optional<LinkRecord> consumeActiveByHash(String hash, Instant now) {
        String sql = "UPDATE smp_account_links SET used_at = ? WHERE code_hash = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > ? RETURNING id,minecraft_uuid,code_hash,expires_at,used_at,revoked_at,revoke_reason";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) { p.setTimestamp(1,Timestamp.from(now)); p.setString(2,hash); p.setTimestamp(3,Timestamp.from(now)); try(ResultSet rs=p.executeQuery()){return rs.next()?Optional.of(read(rs)):Optional.empty();} } catch(SQLException e){throw new LinkPersistenceException(e);}
    }
    @Override public boolean revoke(UUID id, Instant now, String reason) { return execute("UPDATE smp_account_links SET revoked_at=?, revoke_reason=? WHERE id=? AND used_at IS NULL AND revoked_at IS NULL", now, reason, id) == 1; }
    @Override public void audit(LinkAudit a) { execute("INSERT INTO smp_audit_logs (id,target_uuid,action,actor,correlation_id,occurred_at,detail) VALUES (?,?,?,?,?,?,?)", a.id(),a.minecraftUuid(),a.action(),a.actor(),a.correlationId(),a.occurredAt(),a.detail()); }
    private long scalar(String sql, UUID uuid, Instant time) { try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,uuid);p.setTimestamp(2,Timestamp.from(time));try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}}catch(SQLException e){throw new LinkPersistenceException(e);} }
    private int execute(String sql,Object... values){try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++){Object v=values[i];if(v instanceof Instant instant)p.setTimestamp(i+1,Timestamp.from(instant));else p.setObject(i+1,v);}return p.executeUpdate();}catch(SQLException e){throw new LinkPersistenceException(e);}}
    private static LinkRecord read(ResultSet r)throws SQLException{return new LinkRecord(r.getObject("id",UUID.class),r.getObject("minecraft_uuid",UUID.class),r.getString("code_hash"),r.getTimestamp("expires_at").toInstant(),r.getTimestamp("used_at")==null?null:r.getTimestamp("used_at").toInstant(),r.getTimestamp("revoked_at")==null?null:r.getTimestamp("revoked_at").toInstant(),r.getString("revoke_reason"));}
    public static final class LinkPersistenceException extends RuntimeException { LinkPersistenceException(SQLException cause){super("Account-link persistence failure",cause);} }
}
