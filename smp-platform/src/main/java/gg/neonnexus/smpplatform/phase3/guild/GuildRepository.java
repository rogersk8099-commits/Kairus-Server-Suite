package gg.neonnexus.smpplatform.phase3.guild;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Blocking persistence port; invoke through the plugin's async database executor. */
public interface GuildRepository {
    Optional<Guild> findById(UUID guildId);
    Optional<Guild> findByNameOrTag(String nameOrTag);
    Optional<Guild> findByPlayer(UUID playerId);
    List<Guild> topByPoints(int limit);
    Guild insert(Guild guild);
    void replace(Guild guild, long expectedVersion);
    void delete(UUID guildId, long expectedVersion);
    void insertInvite(GuildInvite invite);
    Optional<GuildInvite> findInvite(UUID guildId, UUID targetId, Instant now);
    void deleteInvite(UUID guildId, UUID targetId);
    void deleteExpiredInvites(Instant now);
}
