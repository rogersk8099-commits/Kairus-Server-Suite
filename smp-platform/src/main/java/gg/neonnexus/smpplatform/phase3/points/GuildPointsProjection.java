package gg.neonnexus.smpplatform.phase3.points;

import java.util.UUID;

/**
 * Maintains the denormalized guilds.points leaderboard value in the same database transaction as
 * a GUILD_POINTS account mutation. The parent should use JdbcGuildPointsProjection in production.
 */
@FunctionalInterface
public interface GuildPointsProjection {
    GuildPointsProjection NOOP = (guildId, amount) -> { };
    void apply(UUID guildId, long amount);
}
