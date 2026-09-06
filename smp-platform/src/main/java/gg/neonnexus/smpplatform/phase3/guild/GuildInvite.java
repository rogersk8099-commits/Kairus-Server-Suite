package gg.neonnexus.smpplatform.phase3.guild;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GuildInvite(UUID id, UUID guildId, UUID targetPlayerId, UUID invitedBy, Instant createdAt, Instant expiresAt) {
    public GuildInvite {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        Objects.requireNonNull(invitedBy, "invitedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("invite expiry must be after creation");
    }

    public boolean isExpiredAt(Instant instant) { return !expiresAt.isAfter(instant); }
}
