package com.neonnexus.smpplatform.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** UUID is the durable game identity. XUID supplements Bedrock identity when Floodgate exposes it. */
public record PlayerIdentity(UUID minecraftUuid, String username, Edition edition, String xuid) {
    public enum Edition { JAVA, BEDROCK, UNKNOWN }
    public PlayerIdentity {
        minecraftUuid = Objects.requireNonNull(minecraftUuid); username = Objects.requireNonNull(username).trim(); edition = Objects.requireNonNull(edition);
        if (username.isEmpty()) throw new IllegalArgumentException("username cannot be blank");
        xuid = xuid == null || xuid.isBlank() ? null : xuid.trim();
        if (edition != Edition.BEDROCK && xuid != null) throw new IllegalArgumentException("Only Bedrock identities may hold a XUID");
    }
    public Optional<String> xuidOptional() { return Optional.ofNullable(xuid); }
}
