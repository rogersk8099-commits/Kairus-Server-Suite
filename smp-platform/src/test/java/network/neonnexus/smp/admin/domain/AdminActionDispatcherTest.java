package network.neonnexus.smp.admin.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminActionDispatcherTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Test void destructiveActionDoesNotReachExecutorUntilItsOwnerConfirms() {
        AtomicInteger executions = new AtomicInteger();
        List<AdminActionDispatcher.AuditRecord> audit = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AdminActionDispatcher dispatcher = new AdminActionDispatcher(new PermissionRouter(), new ConfirmationService(clock, Duration.ofSeconds(30)),
                request -> { executions.incrementAndGet(); return AdminActionDispatcher.ActionResult.success("done"); }, audit::add, clock);
        PermissionRouter.PermissionSubject allowed = Set.of("smpplatform.admin.moderation.ban")::contains;
        AdminActionRequest request = new AdminActionRequest(ACTOR, "staff", TARGET, "target", AdminAction.BAN, "", "evidence", clock.instant(), UUID.randomUUID());

        AdminActionDispatcher.DispatchOutcome initial = dispatcher.request(allowed, request);
        assertEquals(AdminActionDispatcher.Status.CONFIRMATION_REQUIRED, initial.status());
        assertEquals(0, executions.get());
        assertEquals(AdminActionDispatcher.Status.DENIED, dispatcher.confirm(permission -> false, ACTOR, initial.confirmationId()).status());
        assertEquals(0, executions.get());

        AdminActionDispatcher.DispatchOutcome second = dispatcher.request(allowed, request);
        assertEquals(AdminActionDispatcher.Status.EXECUTED, dispatcher.confirm(allowed, ACTOR, second.confirmationId()).status());
        assertEquals(1, executions.get());
        assertEquals("EXECUTED", audit.get(audit.size() - 1).outcome());
    }

    @Test void nonDestructiveActionExecutesDirectlyAndIsAudited() {
        AtomicInteger executions = new AtomicInteger();
        List<AdminActionDispatcher.AuditRecord> audit = new ArrayList<>();
        Clock clock = Clock.systemUTC();
        AdminActionDispatcher dispatcher = new AdminActionDispatcher(new PermissionRouter(), new ConfirmationService(clock, Duration.ofSeconds(30)),
                request -> { executions.incrementAndGet(); return AdminActionDispatcher.ActionResult.success("healed"); }, audit::add, clock);
        PermissionRouter.PermissionSubject allowed = Set.of("smpplatform.admin.player.heal")::contains;
        AdminActionRequest request = new AdminActionRequest(ACTOR, "staff", TARGET, "target", AdminAction.HEAL, "", "", clock.instant(), UUID.randomUUID());
        assertEquals(AdminActionDispatcher.Status.EXECUTED, dispatcher.request(allowed, request).status());
        assertEquals(1, executions.get());
        assertEquals("EXECUTED", audit.get(0).outcome());
    }
}
