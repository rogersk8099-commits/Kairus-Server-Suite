package gg.neonnexus.smpplatform.integrations.link;

import java.time.Instant;
import java.util.UUID;

public record LinkRecord(UUID id, UUID minecraftUuid, String codeHash, Instant expiresAt, Instant usedAt, Instant revokedAt, String revokeReason) {
    public boolean activeAt(Instant time) { return usedAt == null && revokedAt == null && expiresAt.isAfter(time); }
}
