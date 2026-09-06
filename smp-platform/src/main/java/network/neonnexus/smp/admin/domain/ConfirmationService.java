package network.neonnexus.smp.admin.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One pending destructive action per actor. A token cannot be consumed by another staff account. */
public final class ConfirmationService {
    private final ConcurrentHashMap<UUID, PendingConfirmation> byOwner = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public ConfirmationService(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
    }

    public PendingConfirmation create(AdminActionRequest request) {
        Instant now = clock.instant();
        PendingConfirmation pending = new PendingConfirmation(UUID.randomUUID(), request.actorId(), request, now.plus(ttl));
        byOwner.put(request.actorId(), pending);
        return pending;
    }

    public Consumption consume(UUID actorId, UUID confirmationId) {
        PendingConfirmation pending = byOwner.get(actorId);
        if (pending == null || !pending.id().equals(confirmationId)) return Consumption.notFound();
        if (clock.instant().isAfter(pending.expiresAt())) {
            byOwner.remove(actorId, pending);
            return Consumption.expired();
        }
        if (byOwner.remove(actorId, pending)) return Consumption.consumed(pending.request());
        return Consumption.notFound();
    }

    public void cancel(UUID actorId) { byOwner.remove(actorId); }
    public Optional<PendingConfirmation> peek(UUID actorId) { return Optional.ofNullable(byOwner.get(actorId)); }

    public record PendingConfirmation(UUID id, UUID ownerId, AdminActionRequest request, Instant expiresAt) { }
    public record Consumption(Status status, AdminActionRequest request) {
        public static Consumption consumed(AdminActionRequest request) { return new Consumption(Status.CONSUMED, request); }
        public static Consumption expired() { return new Consumption(Status.EXPIRED, null); }
        public static Consumption notFound() { return new Consumption(Status.NOT_FOUND, null); }
    }
    public enum Status { CONSUMED, EXPIRED, NOT_FOUND }
}
