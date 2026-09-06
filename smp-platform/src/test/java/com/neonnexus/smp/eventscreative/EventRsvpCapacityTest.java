package com.neonnexus.smp.eventscreative;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventRsvpCapacityTest {
    @Test void capacityLimitWaitlistsThenPromotesInOrder() {
        FakeEvents repository = new FakeEvents();
        UUID eventId = UUID.randomUUID();
        UUID arenaId = UUID.randomUUID();
        repository.arenas.put(arenaId, arena(arenaId));
        ColosseumEvent event = new ColosseumEvent(eventId, "sky-duels", "Sky Duels", EventType.SKY_DUELS, arenaId, UUID.randomUUID(),
            EventLifecycle.REGISTRATION_OPEN, Instant.now(), Instant.now(), 2, 0);
        repository.events.put(eventId, event);
        EventService service = new EventService(repository, new FakeOutbox(), ignored -> { }, Clock.fixed(Instant.parse("2026-10-10T19:00:00Z"), ZoneOffset.UTC));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();

        assertEquals(RegistrationState.REGISTERED, service.rsvp(first, eventId, UUID.randomUUID()).state());
        assertEquals(RegistrationState.REGISTERED, service.rsvp(second, eventId, UUID.randomUUID()).state());
        EventRegistration waitlisted = service.rsvp(third, eventId, UUID.randomUUID());
        assertEquals(RegistrationState.WAITLISTED, waitlisted.state());
        assertEquals(1, waitlisted.queuePosition());

        service.withdraw(first, eventId, UUID.randomUUID());
        EventRegistration promoted = repository.registrations(eventId).stream().filter(r -> r.playerId().equals(third)).findFirst().orElseThrow();
        assertEquals(RegistrationState.REGISTERED, promoted.state());
        assertEquals(null, promoted.queuePosition());
    }

    private static ArenaDefinition arena(UUID id) {
        return new ArenaDefinition(id, "Sky Spire", CanonicalWorlds.COLOSSEUM, EventType.SKY_DUELS,
            List.of(new ArenaDefinition.SpawnPoint(0, 80, 0, 0, 0), new ArenaDefinition.SpawnPoint(5, 80, 0, 0, 0)),
            new ArenaDefinition.SpawnPoint(0, 90, 0, 0, 0), 2, 2, new ArenaDefinition.CuboidBounds(-20, 0, -20, 20, 120, 20), "RESTORE_TEMPLATE", true);
    }

    private static final class FakeEvents implements EventRepository {
        final Map<UUID, ColosseumEvent> events = new HashMap<>(); final Map<UUID, ArenaDefinition> arenas = new HashMap<>();
        final Map<UUID, List<EventRegistration>> registrations = new HashMap<>();
        public Optional<ColosseumEvent> find(UUID id) { return Optional.ofNullable(events.get(id)); }
        public Optional<ArenaDefinition> findArena(UUID id) { return Optional.ofNullable(arenas.get(id)); }
        public List<EventRegistration> registrations(UUID id) { return new ArrayList<>(registrations.getOrDefault(id, List.of())); }
        public void saveEvent(ColosseumEvent event) { events.put(event.id(), event); }
        public void saveRegistration(EventRegistration r) { var list = registrations.computeIfAbsent(r.eventId(), ignored -> new ArrayList<>()); list.removeIf(old -> old.playerId().equals(r.playerId())); list.add(r); }
        public void saveTeam(EventTeam team) { } public void saveRound(EventRound round) { } public void saveMatch(EventMatch match) { }
        public void saveResult(UUID eventId, EventResult result) { } public void saveReward(UUID eventId, EventReward reward) { }
        public <T> T inTransaction(TransactionalWork<T> work) { return work.run(); }
    }
    private static final class FakeOutbox implements OutboxRepository {
        public void enqueue(PlatformOutboxEvent event) { } public List<PlatformOutboxEvent> claimReady(Instant now, int limit) { return List.of(); }
        public void markDelivered(UUID eventId, Instant deliveredAt) { } public void reschedule(PlatformOutboxEvent event, String error) { }
    }
}
