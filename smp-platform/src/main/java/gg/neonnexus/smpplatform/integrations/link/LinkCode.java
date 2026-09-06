package gg.neonnexus.smpplatform.integrations.link;

import java.time.Instant;
import java.util.UUID;

/** Plaintext code exists only in the command response, never in database records or audit details. */
public record LinkCode(UUID id, UUID minecraftUuid, String code, Instant expiresAt) { }
