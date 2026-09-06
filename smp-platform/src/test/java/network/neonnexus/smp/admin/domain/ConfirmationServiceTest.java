package network.neonnexus.smp.admin.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfirmationServiceTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test void confirmationIsOwnerBoundAndSingleUse() {
        ConfirmationService service = new ConfirmationService(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), Duration.ofSeconds(30));
        ConfirmationService.PendingConfirmation pending = service.create(request());
        assertEquals(ConfirmationService.Status.NOT_FOUND, service.consume(OTHER, pending.id()).status());
        assertEquals(ConfirmationService.Status.CONSUMED, service.consume(ACTOR, pending.id()).status());
        assertEquals(ConfirmationService.Status.NOT_FOUND, service.consume(ACTOR, pending.id()).status());
    }

    @Test void expiredConfirmationCannotBeConsumed() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ConfirmationService service = new ConfirmationService(Clock.offset(Clock.fixed(start, ZoneOffset.UTC), Duration.ofSeconds(31)), Duration.ofSeconds(30));
        // Create using a clock at the intended issue time, then validate expiration with a separately advanced service via deterministic request.
        ConfirmationService atIssue = new ConfirmationService(Clock.fixed(start, ZoneOffset.UTC), Duration.ofSeconds(30));
        ConfirmationService.PendingConfirmation pending = atIssue.create(request());
        assertEquals(ConfirmationService.Status.CONSUMED, atIssue.consume(ACTOR, pending.id()).status());
        ConfirmationService expired = new ConfirmationService(Clock.fixed(start.plusSeconds(31), ZoneOffset.UTC), Duration.ofSeconds(30));
        ConfirmationService.PendingConfirmation expiredPending = expired.create(request());
        // Advance expires-at deterministically by issuing a request with a clock whose present is after an externally supplied expiry is impossible; test strict expiry through custom mutable clock below.
        MutableClock clock = new MutableClock(start);
        ConfirmationService mutable = new ConfirmationService(clock, Duration.ofSeconds(30));
        ConfirmationService.PendingConfirmation token = mutable.create(request());
        clock.now = start.plusSeconds(31);
        assertEquals(ConfirmationService.Status.EXPIRED, mutable.consume(ACTOR, token.id()).status());
    }

    private static AdminActionRequest request() { return new AdminActionRequest(ACTOR, "Actor", TARGET, "Target", AdminAction.BAN, "", "test", Instant.EPOCH, UUID.randomUUID()); }
    private static final class MutableClock extends Clock {
        private Instant now; MutableClock(Instant now) { this.now = now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
