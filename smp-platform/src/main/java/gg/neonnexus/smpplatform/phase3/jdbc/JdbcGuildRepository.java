package gg.neonnexus.smpplatform.phase3.jdbc;

import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.guild.Guild;
import gg.neonnexus.smpplatform.phase3.guild.GuildInvite;
import gg.neonnexus.smpplatform.phase3.guild.GuildMember;
import gg.neonnexus.smpplatform.phase3.guild.GuildRank;
import gg.neonnexus.smpplatform.phase3.guild.GuildRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL guild repository. Call it only inside {@link JdbcTransactionRunner#required}. */
public final class JdbcGuildRepository implements GuildRepository {
    private final JdbcTransactionRunner transactions;

    public JdbcGuildRepository(JdbcTransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override public Optional<Guild> findById(UUID guildId) {
        return findBySql("SELECT guild_id FROM smp_guilds WHERE guild_id=?", statement -> statement.setObject(1, guildId));
    }

    @Override public Optional<Guild> findByNameOrTag(String nameOrTag) {
        return findBySql("SELECT guild_id FROM smp_guilds WHERE LOWER(name)=LOWER(?) OR tag=UPPER(?)", statement -> {
            statement.setString(1, nameOrTag); statement.setString(2, nameOrTag);
        });
    }

    @Override public Optional<Guild> findByPlayer(UUID playerId) {
        return findBySql("SELECT guild_id FROM smp_guild_members WHERE player_id=?", statement -> statement.setObject(1, playerId));
    }

    @Override public List<Guild> topByPoints(int limit) {
        try (PreparedStatement statement = connection().prepareStatement("SELECT guild_id FROM smp_guilds ORDER BY points DESC, created_at ASC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<Guild> smp_guilds = new ArrayList<>();
                while (result.next()) smp_guilds.add(load(result.getObject(1, UUID.class)));
                return List.copyOf(smp_guilds);
            }
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public Guild insert(Guild guild) {
        try (PreparedStatement insertGuild = connection().prepareStatement("INSERT INTO smp_guilds (guild_id,name,tag,description,owner_id,created_at,points,version) VALUES (?,?,?,?,?,?,?,?)")) {
            insertGuild.setObject(1, guild.id()); insertGuild.setString(2, guild.name()); insertGuild.setString(3, guild.tag()); insertGuild.setString(4, guild.description());
            insertGuild.setObject(5, guild.ownerId()); insertGuild.setTimestamp(6, Timestamp.from(guild.createdAt())); insertGuild.setLong(7, guild.points()); insertGuild.setLong(8, guild.version());
            insertGuild.executeUpdate();
            insertMembers(guild.id(), guild.members());
            return guild;
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public void replace(Guild guild, long expectedVersion) {
        try (PreparedStatement update = connection().prepareStatement("UPDATE smp_guilds SET tag=?,description=?,owner_id=?,points=?,version=? WHERE guild_id=? AND version=?")) {
            update.setString(1, guild.tag()); update.setString(2, guild.description()); update.setObject(3, guild.ownerId()); update.setLong(4, guild.points()); update.setLong(5, guild.version()); update.setObject(6, guild.id()); update.setLong(7, expectedVersion);
            if (update.executeUpdate() != 1) throw new Phase3Exception(Phase3Exception.Code.CONCURRENT_MODIFICATION, "Guild changed concurrently");
            try (PreparedStatement deleteMembers = connection().prepareStatement("DELETE FROM smp_guild_members WHERE guild_id=?")) {
                deleteMembers.setObject(1, guild.id()); deleteMembers.executeUpdate();
            }
            insertMembers(guild.id(), guild.members());
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public void delete(UUID guildId, long expectedVersion) {
        try (PreparedStatement delete = connection().prepareStatement("DELETE FROM smp_guilds WHERE guild_id=? AND version=?")) {
            delete.setObject(1, guildId); delete.setLong(2, expectedVersion);
            if (delete.executeUpdate() != 1) throw new Phase3Exception(Phase3Exception.Code.CONCURRENT_MODIFICATION, "Guild changed concurrently");
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public void insertInvite(GuildInvite invite) {
        try (PreparedStatement statement = connection().prepareStatement("INSERT INTO smp_guild_invites (invite_id,guild_id,target_player_id,invited_by,created_at,expires_at) VALUES (?,?,?,?,?,?)")) {
            statement.setObject(1, invite.id()); statement.setObject(2, invite.guildId()); statement.setObject(3, invite.targetPlayerId()); statement.setObject(4, invite.invitedBy());
            statement.setTimestamp(5, Timestamp.from(invite.createdAt())); statement.setTimestamp(6, Timestamp.from(invite.expiresAt())); statement.executeUpdate();
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public Optional<GuildInvite> findInvite(UUID guildId, UUID targetId, Instant now) {
        try (PreparedStatement statement = connection().prepareStatement("SELECT invite_id,invited_by,created_at,expires_at FROM smp_guild_invites WHERE guild_id=? AND target_player_id=? AND expires_at>?")) {
            statement.setObject(1, guildId); statement.setObject(2, targetId); statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new GuildInvite(result.getObject(1, UUID.class), guildId, targetId, result.getObject(2, UUID.class), result.getTimestamp(3).toInstant(), result.getTimestamp(4).toInstant()));
            }
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public List<GuildInvite> findInvitesFor(UUID targetId, Instant now) {
        try (PreparedStatement statement = connection().prepareStatement("SELECT invite_id,guild_id,invited_by,created_at,expires_at FROM smp_guild_invites WHERE target_player_id=? AND expires_at>? ORDER BY created_at ASC")) {
            statement.setObject(1, targetId); statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                List<GuildInvite> invites = new ArrayList<>();
                while (result.next()) invites.add(new GuildInvite(result.getObject(1, UUID.class), result.getObject(2, UUID.class), targetId, result.getObject(3, UUID.class), result.getTimestamp(4).toInstant(), result.getTimestamp(5).toInstant()));
                return List.copyOf(invites);
            }
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public void deleteInvite(UUID guildId, UUID targetId) {
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM smp_guild_invites WHERE guild_id=? AND target_player_id=?")) {
            statement.setObject(1, guildId); statement.setObject(2, targetId); statement.executeUpdate();
        } catch (SQLException exception) { throw storage(exception); }
    }

    @Override public void deleteExpiredInvites(Instant now) {
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM smp_guild_invites WHERE expires_at<=?")) {
            statement.setTimestamp(1, Timestamp.from(now)); statement.executeUpdate();
        } catch (SQLException exception) { throw storage(exception); }
    }

    private Optional<Guild> findBySql(String sql, Binder binder) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? Optional.of(load(result.getObject(1, UUID.class))) : Optional.empty(); }
        } catch (SQLException exception) { throw storage(exception); }
    }

    private Guild load(UUID id) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT name,tag,description,owner_id,created_at,points,version FROM smp_guilds WHERE guild_id=?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new Phase3Exception(Phase3Exception.Code.NOT_FOUND, "Guild not found");
                return new Guild(id, result.getString(1), result.getString(2), result.getString(3), result.getObject(4, UUID.class), result.getTimestamp(5).toInstant(), result.getLong(6), result.getLong(7), members(id));
            }
        }
    }

    private List<GuildMember> members(UUID guildId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT player_id,rank,joined_at FROM smp_guild_members WHERE guild_id=? ORDER BY joined_at ASC")) {
            statement.setObject(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                List<GuildMember> members = new ArrayList<>();
                while (result.next()) members.add(new GuildMember(result.getObject(1, UUID.class), GuildRank.valueOf(result.getString(2)), result.getTimestamp(3).toInstant()));
                return List.copyOf(members);
            }
        }
    }

    private void insertMembers(UUID guildId, List<GuildMember> members) throws SQLException {
        try (PreparedStatement insert = connection().prepareStatement("INSERT INTO smp_guild_members (guild_id,player_id,rank,joined_at) VALUES (?,?,?,?)")) {
            for (GuildMember member : members) {
                insert.setObject(1, guildId); insert.setObject(2, member.playerId()); insert.setString(3, member.rank().name()); insert.setTimestamp(4, Timestamp.from(member.joinedAt())); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private Connection connection() { return transactions.connection(); }
    private static Phase3Exception storage(SQLException exception) { return new Phase3Exception(Phase3Exception.Code.FAILED_STORAGE, "Guild persistence unavailable; no mutation committed", exception); }
    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
}
