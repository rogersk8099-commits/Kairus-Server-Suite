package com.neonnexus.smp.eventscreative;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventStateAndCapacityTest {
    @Test void onlyTheDefinedForwardLifecycleIsLegal() {
        assertTrue(EventStateMachine.canTransition(EventLifecycle.DRAFT, EventLifecycle.SCHEDULED));
        assertTrue(EventStateMachine.canTransition(EventLifecycle.SCHEDULED, EventLifecycle.REGISTRATION_OPEN));
        assertTrue(EventStateMachine.canTransition(EventLifecycle.REGISTRATION_OPEN, EventLifecycle.CHECK_IN));
        assertTrue(EventStateMachine.canTransition(EventLifecycle.CHECK_IN, EventLifecycle.STARTING));
        assertTrue(EventStateMachine.canTransition(EventLifecycle.STARTING, EventLifecycle.LIVE));
        assertTrue(EventStateMachine.canTransition(EventLifecycle.LIVE, EventLifecycle.FINISHED));
        assertTrue(EventStateMachine.canTransition(EventLifecycle.FINISHED, EventLifecycle.ARCHIVED));
        assertFalse(EventStateMachine.canTransition(EventLifecycle.DRAFT, EventLifecycle.LIVE));
        assertFalse(EventStateMachine.canTransition(EventLifecycle.ARCHIVED, EventLifecycle.DRAFT));
        assertThrows(IllegalStateException.class, () -> EventStateMachine.requireTransition(EventLifecycle.LIVE, EventLifecycle.CHECK_IN));
    }

    @Test void gauntletNeverExceedsSixtyFourParticipants() {
        assertDoesNotThrow(() -> event(EventType.GAUNTLET, 64));
        assertThrows(IllegalArgumentException.class, () -> event(EventType.GAUNTLET, 65));
    }

    @Test void otherEventTypesStillRequireAPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> event(EventType.SKY_DUELS, 0));
        assertDoesNotThrow(() -> event(EventType.BLOCK_RUSH, 128));
    }

    @Test void arenaRequiresEnoughSpawnsForMinimumPlayers() {
        assertThrows(IllegalArgumentException.class, () -> new ArenaDefinition(UUID.randomUUID(), "Gauntlet", CanonicalWorlds.COLOSSEUM,
            EventType.GAUNTLET, List.of(new ArenaDefinition.SpawnPoint(0, 80, 0, 0, 0)), new ArenaDefinition.SpawnPoint(0, 100, 0, 0, 0),
            2, 64, new ArenaDefinition.CuboidBounds(0, 0, 0, 20, 120, 20), "RESTORE_TEMPLATE", true));
    }

    private ColosseumEvent event(EventType type, int capacity) {
        return new ColosseumEvent(UUID.randomUUID(), "test-event", "Test Event", type, UUID.randomUUID(), UUID.randomUUID(),
            EventLifecycle.DRAFT, Instant.parse("2026-10-10T20:00:00Z"), Instant.parse("2026-10-10T19:30:00Z"), capacity, 0);
    }
}
