package gg.neonnexus.smpplatform.integrations.link;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository {
    long countGeneratedSince(UUID minecraftUuid, Instant since);
    void create(LinkRecord record);
    /** Atomic compare-and-set consumption: must return empty after use, revoke, or expiry. */
    Optional<LinkRecord> consumeActiveByHash(String codeHash, Instant now);
    boolean revoke(UUID linkId, Instant now, String reason);
    void audit(LinkAudit audit);
}
