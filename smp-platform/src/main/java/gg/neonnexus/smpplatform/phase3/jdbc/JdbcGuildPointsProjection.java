package gg.neonnexus.smpplatform.phase3.jdbc;

import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.points.GuildPointsProjection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Updates guild leaderboard points inside the caller's active JDBC transaction. */
public final class JdbcGuildPointsProjection implements GuildPointsProjection {
    private final JdbcTransactionRunner transactions;

    public JdbcGuildPointsProjection(JdbcTransactionRunner transactions) { this.transactions = Objects.requireNonNull(transactions, "transactions"); }

    @Override public void apply(UUID guildId, long amount) {
        try (PreparedStatement statement = transactions.connection().prepareStatement("UPDATE smp_guilds SET points=points+?, version=version+1 WHERE guild_id=? AND points+?>=0")) {
            statement.setLong(1, amount); statement.setObject(2, guildId); statement.setLong(3, amount);
            if (statement.executeUpdate() != 1) throw new Phase3Exception(Phase3Exception.Code.INSUFFICIENT_BALANCE, "Guild points cannot become negative or guild is missing");
        } catch (SQLException exception) {
            throw new Phase3Exception(Phase3Exception.Code.FAILED_STORAGE, "Guild points projection failed; no mutation committed", exception);
        }
    }
}
