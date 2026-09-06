package gg.neonnexus.smpplatform.phase3.guild;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GuildMember(UUID playerId, GuildRank rank, Instant joinedAt) {
    public GuildMember {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
