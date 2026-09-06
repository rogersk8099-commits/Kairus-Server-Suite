package gg.neonnexus.smpplatform.integrations.link;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Short-lived, single-use account-link code flow. Database mutation is invoked by an async caller, never a Bukkit thread. */
public final class AccountLinkService {
    public static final int MAX_CODES_PER_WINDOW = 3;
    public static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final LinkRepository repository; private final CodeHasher hasher; private final SecureRandom random; private final Clock clock; private final Duration ttl;
    public AccountLinkService(LinkRepository repository, CodeHasher hasher, SecureRandom random, Clock clock, Duration ttl) {
        this.repository = Objects.requireNonNull(repository); this.hasher = Objects.requireNonNull(hasher); this.random = Objects.requireNonNull(random); this.clock = Objects.requireNonNull(clock); this.ttl = Objects.requireNonNull(ttl);
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) throw new IllegalArgumentException("Link TTL must be 1 second to 1 hour");
    }
    public LinkCode generate(UUID minecraftUuid, String actor, String correlationId) {
        Instant now = clock.instant();
        if (repository.countGeneratedSince(minecraftUuid, now.minus(RATE_LIMIT_WINDOW)) >= MAX_CODES_PER_WINDOW) {
            repository.audit(audit(minecraftUuid, "LINK_RATE_LIMITED", actor, correlationId, now, "Generation limit exceeded"));
            throw new LinkRateLimitedException("Please wait before generating another Neon Nexus link code.");
        }
        UUID id = UUID.randomUUID(); String code = nextCode(); Instant expires = now.plus(ttl);
        repository.create(new LinkRecord(id, minecraftUuid, hasher.hash(code), expires, null, null, null));
        repository.audit(audit(minecraftUuid, "LINK_CODE_GENERATED", actor, correlationId, now, "expiresAt=" + expires));
        return new LinkCode(id, minecraftUuid, code, expires);
    }
    public Optional<LinkRecord> consume(String submittedCode, String actor, String correlationId) {
        Instant now = clock.instant();
        Optional<LinkRecord> consumed = repository.consumeActiveByHash(hasher.hash(normalize(submittedCode)), now);
        consumed.ifPresentOrElse(record -> repository.audit(audit(record.minecraftUuid(), "LINK_CODE_CONSUMED", actor, correlationId, now, "linkId=" + record.id())),
            () -> repository.audit(new LinkAudit(UUID.randomUUID(), null, "LINK_CODE_REJECTED", actor, correlationId, now, "Invalid, expired, used, or revoked code")));
        return consumed;
    }
    public boolean revoke(UUID linkId, UUID minecraftUuid, String actor, String correlationId, String reason) {
        Instant now = clock.instant(); boolean revoked = repository.revoke(linkId, now, safeReason(reason));
        repository.audit(audit(minecraftUuid, revoked ? "LINK_CODE_REVOKED" : "LINK_REVOKE_MISSED", actor, correlationId, now, "linkId=" + linkId));
        return revoked;
    }
    private String nextCode() {
        StringBuilder raw = new StringBuilder(6); for (int i = 0; i < 6; i++) raw.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return "NX-" + raw.substring(0, 4) + "-" + raw.substring(4);
    }
    public static String normalize(String code) { return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", ""); }
    private static String safeReason(String reason) { String value = reason == null ? "No reason supplied" : reason.strip(); return value.substring(0, Math.min(200, value.length())); }
    private static LinkAudit audit(UUID minecraftUuid, String action, String actor, String correlationId, Instant now, String detail) { return new LinkAudit(UUID.randomUUID(), minecraftUuid, action, actor == null ? "SYSTEM" : actor, correlationId == null ? UUID.randomUUID().toString() : correlationId, now, detail); }
    public static final class LinkRateLimitedException extends RuntimeException { public LinkRateLimitedException(String message) { super(message); } }
}
