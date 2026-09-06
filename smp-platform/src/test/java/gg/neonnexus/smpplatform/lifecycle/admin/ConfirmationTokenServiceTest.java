package gg.neonnexus.smpplatform.lifecycle.admin;

import static org.junit.jupiter.api.Assertions.*;
import gg.neonnexus.smpplatform.lifecycle.audit.InMemoryAuditLogRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConfirmationTokenServiceTest {
    @Test void tokenIsActorBoundOneTimeAndAudited() {
        InMemoryAuditLogRepository audit = new InMemoryAuditLogRepository();
        ConfirmationTokenService service = new ConfirmationTokenService(audit);
        UUID actor = UUID.randomUUID(); AtomicBoolean invoked = new AtomicBoolean();
        String token = service.issue(actor, "QUARRY_RESET", "quarry", "quarry", "test", Duration.ofMinutes(1), () -> invoked.set(true));
        assertEquals(ConfirmationTokenService.ConfirmationResult.REJECTED, service.confirm(UUID.randomUUID(), token));
        assertFalse(invoked.get());
        String valid = service.issue(actor, "QUARRY_RESET", "quarry", "quarry", "test", Duration.ofMinutes(1), () -> invoked.set(true));
        assertEquals(ConfirmationTokenService.ConfirmationResult.CONFIRMED, service.confirm(actor, valid));
        assertTrue(invoked.get());
        assertEquals(ConfirmationTokenService.ConfirmationResult.INVALID, service.confirm(actor, valid));
        assertEquals(4, audit.entries().size());
    }
}
