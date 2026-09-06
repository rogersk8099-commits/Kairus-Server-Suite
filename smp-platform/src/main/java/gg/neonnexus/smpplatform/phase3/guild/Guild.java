package gg.neonnexus.smpplatform.phase3.guild;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable guild aggregate returned by repositories and services. */
public record Guild(
        UUID id,
        String name,
        String tag,
        String description,
        UUID ownerId,
        Instant createdAt,
        long points,
        long version,
        List<GuildMember> members
) {
    public Guild {
        Objects.requireNonNull(id, "id");
        name = GuildText.requireName(name);
        tag = GuildText.requireTag(tag);
        description = GuildText.normalizeDescription(description);
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (points < 0) throw new IllegalArgumentException("points must not be negative");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        long leaders = members.stream().filter(member -> member.rank() == GuildRank.LEADER).count();
        if (leaders != 1) throw new IllegalArgumentException("guild must have exactly one leader");
        GuildMember leader = members.stream().filter(member -> member.rank() == GuildRank.LEADER).findFirst().orElseThrow();
        if (!leader.playerId().equals(ownerId)) throw new IllegalArgumentException("owner must be the leader");
    }

    public GuildMember member(UUID playerId) {
        return members.stream().filter(member -> member.playerId().equals(playerId)).findFirst().orElse(null);
    }

    public boolean hasMember(UUID playerId) { return member(playerId) != null; }
}
