package gg.neonnexus.smpplatform.integrations.link;

import java.time.Instant;
import java.util.UUID;

public record LinkAudit(UUID id, UUID minecraftUuid, String action, String actor, String correlationId, Instant occurredAt, String detail) { }
