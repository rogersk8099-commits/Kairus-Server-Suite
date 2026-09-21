package gg.neonnexus.smpplatform.phase3.jdbc;

import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.points.PointsRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static gg.neonnexus.smpplatform.phase3.points.PointsDomain.*;

/** PostgreSQL repository. Its caller must surround applyAtomically in JdbcTransactionRunner.required. */
public final class JdbcPointsRepository implements PointsRepository {
    private final JdbcTransactionRunner transactions;
    public JdbcPointsRepository(JdbcTransactionRunner transactions) { this.transactions = Objects.requireNonNull(transactions); }
    @Override public PointTransaction applyAtomically(BalanceChange change, boolean allowNegative, Instant now) {
        try {
            Connection c = transactions.connection(); PointAccount current = lockOrCreate(c, change.account(), now);
            long after = Math.addExact(current.balance(), change.amount());
            if (!allowNegative && after < 0) throw new Phase3Exception(Phase3Exception.Code.INSUFFICIENT_BALANCE, "Insufficient " + change.account().currencyId() + " balance");
            try (PreparedStatement update = c.prepareStatement("UPDATE smp_point_accounts SET balance=?, version=version+1, updated_at=? WHERE account_id=? AND version=?")) {
                update.setLong(1, after); update.setTimestamp(2, Timestamp.from(now)); update.setObject(3, current.id()); update.setLong(4, current.version());
                if (update.executeUpdate() != 1) throw new Phase3Exception(Phase3Exception.Code.CONCURRENT_MODIFICATION, "Points account changed concurrently");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO smp_point_transactions (transaction_id,account_id,currency_id,amount,balance_before,balance_after,source,reason,metadata,world_id,actor_id,correlation_id,occurred_at) VALUES (?,?,?,?,?,?,?,? ,CAST(? AS jsonb),?,?,?,?)")) {
                insert.setObject(1,id); insert.setObject(2,current.id()); insert.setString(3,change.account().currencyId()); insert.setLong(4,change.amount()); insert.setLong(5,current.balance()); insert.setLong(6,after); insert.setString(7,change.source().name()); insert.setString(8,change.reason()); insert.setString(9,JsonStringMap.write(change.metadata()));
                if (change.world()==null) insert.setNull(10,Types.VARCHAR); else insert.setString(10,change.world().id()); if (change.actorId()==null) insert.setNull(11,Types.OTHER); else insert.setObject(11,change.actorId()); insert.setObject(12,change.correlationId()); insert.setTimestamp(13,Timestamp.from(now)); insert.executeUpdate();
            }
            return new PointTransaction(id,current.id(),change.account().currencyId(),change.amount(),current.balance(),after,change.source(),change.reason(),change.metadata(),change.world(),change.actorId(),change.correlationId(),now);
        } catch (SQLException ex) { throw storage(ex); }
          catch (ArithmeticException ex) { throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT, "Points amount overflows a 64-bit balance", ex); }
    }
    private PointAccount lockOrCreate(Connection c, AccountKey key, Instant now) throws SQLException {
        PointAccount locked = select(c,key,true); if (locked != null) return locked;
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert=c.prepareStatement("INSERT INTO smp_point_accounts (account_id,owner_type,owner_id,currency_id,balance,version,created_at,updated_at) VALUES (?,?,?,?,0,0,?,?) ON CONFLICT (owner_type,owner_id,currency_id) DO NOTHING")) { insert.setObject(1,id);insert.setString(2,key.ownerType().name());insert.setObject(3,key.ownerId());insert.setString(4,key.currencyId());insert.setTimestamp(5,Timestamp.from(now));insert.setTimestamp(6,Timestamp.from(now));insert.executeUpdate(); }
        PointAccount created=select(c,key,true); if(created==null) throw new SQLException("Unable to lock points account"); return created;
    }
    @Override public Optional<PointAccount> findAccount(AccountKey key) { try { return Optional.ofNullable(select(transactions.connection(),key,false)); } catch(SQLException ex){throw storage(ex);} }
    private PointAccount select(Connection c,AccountKey key,boolean locked)throws SQLException { String sql="SELECT account_id,balance,version,created_at,updated_at FROM smp_point_accounts WHERE owner_type=? AND owner_id=? AND currency_id=?"+(locked?" FOR UPDATE":""); try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,key.ownerType().name());ps.setObject(2,key.ownerId());ps.setString(3,key.currencyId());try(ResultSet r=ps.executeQuery()){return r.next()?new PointAccount((UUID)r.getObject(1),key,r.getLong(2),r.getLong(3),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant()):null;}} }
    @Override public List<PointTransaction> history(AccountKey key,int limit,Instant before) { try { String q="SELECT t.transaction_id,t.account_id,t.currency_id,t.amount,t.balance_before,t.balance_after,t.source,t.reason,t.metadata::text,t.world_id,t.actor_id,t.correlation_id,t.occurred_at FROM smp_point_transactions t JOIN smp_point_accounts a ON a.account_id=t.account_id WHERE a.owner_type=? AND a.owner_id=? AND a.currency_id=? AND t.occurred_at<? ORDER BY t.occurred_at DESC LIMIT ?"; try(PreparedStatement ps=transactions.connection().prepareStatement(q)){ps.setString(1,key.ownerType().name());ps.setObject(2,key.ownerId());ps.setString(3,key.currencyId());ps.setTimestamp(4,Timestamp.from(before));ps.setInt(5,limit);try(ResultSet r=ps.executeQuery()){List<PointTransaction> out=new ArrayList<>();while(r.next())out.add(row(r));return List.copyOf(out);}} }catch(SQLException ex){throw storage(ex);} }
    @Override public List<LeaderboardEntry> leaderboard(String currency,OwnerType type,int limit) { try { String q="SELECT owner_id,balance FROM smp_point_accounts WHERE currency_id=? AND owner_type=? ORDER BY balance DESC, owner_id ASC LIMIT ?";try(PreparedStatement ps=transactions.connection().prepareStatement(q)){ps.setString(1,currency);ps.setString(2,type.name());ps.setInt(3,limit);try(ResultSet r=ps.executeQuery()){List<LeaderboardEntry> out=new ArrayList<>();int rank=0;while(r.next()){rank++;out.add(new LeaderboardEntry(new AccountKey(type,(UUID)r.getObject(1),currency),r.getLong(2),rank));}return List.copyOf(out);}}}catch(SQLException ex){throw storage(ex);} }
    @Override public boolean claimAutomaticReward(String rewardKey,UUID playerId,Instant claimedAt) { try(PreparedStatement ps=transactions.connection().prepareStatement("INSERT INTO smp_automatic_point_rewards (reward_key,player_id,claimed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING")){ps.setString(1,rewardKey);ps.setObject(2,playerId);ps.setTimestamp(3,Timestamp.from(claimedAt));return ps.executeUpdate()==1;}catch(SQLException ex){throw storage(ex);} }
    private static PointTransaction row(ResultSet r)throws SQLException { String wid=r.getString(10); java.util.Optional<gg.neonnexus.smpplatform.phase3.world.NeonWorld> world=wid==null?Optional.empty():gg.neonnexus.smpplatform.phase3.world.NeonWorld.fromId(wid); return new PointTransaction((UUID)r.getObject(1),(UUID)r.getObject(2),r.getString(3),r.getLong(4),r.getLong(5),r.getLong(6),Source.valueOf(r.getString(7)),r.getString(8),JsonStringMap.read(r.getString(9)),world.orElse(null),(UUID)r.getObject(11),(UUID)r.getObject(12),r.getTimestamp(13).toInstant()); }
    private static Phase3Exception storage(SQLException ex){return new Phase3Exception(Phase3Exception.Code.FAILED_STORAGE,"Points persistence unavailable; no mutation committed",ex);}
}
