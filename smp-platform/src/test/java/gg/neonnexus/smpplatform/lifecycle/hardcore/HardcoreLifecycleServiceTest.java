package gg.neonnexus.smpplatform.lifecycle.hardcore;

import static org.junit.jupiter.api.Assertions.*;
import gg.neonnexus.smpplatform.lifecycle.events.DurableEvent;
import gg.neonnexus.smpplatform.lifecycle.events.DurableEventOutbox;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HardcoreLifecycleServiceTest {
    @Test void deathEvidenceIsStoredAndPlayerEndsSpectating() {
        InMemoryHardcoreRepository store = new InMemoryHardcoreRepository();
        List<DurableEvent> events = new ArrayList<>();
        DurableEventOutbox outbox = events::add;
        HardcoreLifecycleService service = new HardcoreLifecycleService(store, store, outbox);
        UUID player = UUID.randomUUID(); Instant at = Instant.parse("2025-01-01T12:00:00Z");
        service.registerIfAbsent(player, "Season 7", at.minus(Duration.ofDays(2)));
        HardcorePlayer state = service.recordDeath(new DeathCapture(player, "Season 7", "slain by Zombie", null, null,
                new WorldPosition("obsidian-gate", 10, 64, -8, 0, 0), Duration.ofDays(2), HardcoreStatistics.empty(), at), "c-1");
        assertEquals(HardcoreState.SPECTATING, state.state());
        assertEquals(1, store.deaths().size());
        assertEquals("slain by Zombie", store.deaths().getFirst().cause());
        assertEquals(List.of("HARDCORE_DEATH", "HARDCORE_SPECTATING"), events.stream().map(DurableEvent::type).toList());
    }
}
