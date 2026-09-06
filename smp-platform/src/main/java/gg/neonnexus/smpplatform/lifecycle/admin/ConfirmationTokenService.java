package gg.neonnexus.smpplatform.lifecycle.admin;

import gg.neonnexus.smpplatform.lifecycle.audit.AuditEntry;
import gg.neonnexus.smpplatform.lifecycle.audit.AuditLogRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One-time, actor-bound, expiring confirmation tokens for destructive/admin lifecycle operations. */
public final class ConfirmationTokenService {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new HashMap<>();
    private final AuditLogRepository audit;
    public ConfirmationTokenService(AuditLogRepository audit) { this.audit = audit; }

    public synchronized String issue(UUID actor, String operation, String target, String worldId, String reason, Duration ttl, Runnable action) {
        byte[] bytes = new byte[18]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String correlationId = UUID.randomUUID().toString();
        Instant expires = Instant.now().plus(ttl);
        pending.put(token, new Pending(actor, operation, target, worldId, reason, expires, correlationId, action));
        audit.append(new AuditEntry(UUID.randomUUID(), Instant.now(), actor, "ADMIN_CONFIRMATION_ISSUED", target, worldId,
                null, null, reason, correlationId, Map.of("operation", operation, "expiresAt", expires.toString())));
        return token;
    }

    public synchronized ConfirmationResult confirm(UUID actor, String token) {
        Pending value = pending.remove(token);
        if (value == null) return ConfirmationResult.INVALID;
        if (!value.actor().equals(actor)) { audit(value, actor, "ADMIN_CONFIRMATION_REJECTED", "actor-mismatch"); return ConfirmationResult.REJECTED; }
        if (Instant.now().isAfter(value.expiresAt())) { audit(value, actor, "ADMIN_CONFIRMATION_EXPIRED", "expired"); return ConfirmationResult.EXPIRED; }
        try {
            value.action().run();
            audit(value, actor, "ADMIN_CONFIRMATION_CONFIRMED", "confirmed");
            return ConfirmationResult.CONFIRMED;
        } catch (RuntimeException exception) {
            audit(value, actor, "ADMIN_CONFIRMATION_FAILED", exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private void audit(Pending value, UUID actor, String action, String outcome) {
        audit.append(new AuditEntry(UUID.randomUUID(), Instant.now(), actor, action, value.target(), value.worldId(), null, null,
                value.reason(), value.correlationId(), Map.of("operation", value.operation(), "outcome", outcome)));
    }
    private record Pending(UUID actor, String operation, String target, String worldId, String reason, Instant expiresAt, String correlationId, Runnable action) { }
    public enum ConfirmationResult { CONFIRMED, INVALID, REJECTED, EXPIRED }
}
