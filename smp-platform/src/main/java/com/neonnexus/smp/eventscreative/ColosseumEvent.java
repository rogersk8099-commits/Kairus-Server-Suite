package com.neonnexus.smp.eventscreative;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mutable aggregate whose mutations are coordinated by EventService and persisted atomically. */
public final class ColosseumEvent {
    private final UUID id;
    private final String slug;
    private final String name;
    private final EventType type;
    private final UUID arenaId;
    private final UUID createdBy;
    private EventLifecycle state;
    private Instant scheduledAt;
    private Instant checkInOpensAt;
    private int registrationLimit;
    private long revision;

    public ColosseumEvent(UUID id, String slug, String name, EventType type, UUID arenaId, UUID createdBy,
                          EventLifecycle state, Instant scheduledAt, Instant checkInOpensAt, int registrationLimit, long revision) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = required(slug, "slug");
        this.name = required(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.state = Objects.requireNonNull(state, "state");
        this.scheduledAt = scheduledAt;
        this.checkInOpensAt = checkInOpensAt;
        if (registrationLimit < 1) throw new IllegalArgumentException("registrationLimit must be positive");
        if (type == EventType.GAUNTLET && registrationLimit > 64) throw new IllegalArgumentException("A Gauntlet supports at most 64 participants");
        this.registrationLimit = registrationLimit;
        this.revision = revision;
    }

    public UUID id() { return id; }
    public String slug() { return slug; }
    public String name() { return name; }
    public EventType type() { return type; }
    public UUID arenaId() { return arenaId; }
    public UUID createdBy() { return createdBy; }
    public EventLifecycle state() { return state; }
    public Instant scheduledAt() { return scheduledAt; }
    public Instant checkInOpensAt() { return checkInOpensAt; }
    public int registrationLimit() { return registrationLimit; }
    public long revision() { return revision; }
    public String worldId() { return CanonicalWorlds.COLOSSEUM; }

    public void transitionTo(EventLifecycle target) {
        EventStateMachine.requireTransition(state, target);
        state = target;
        revision++;
    }

    public boolean acceptsRsvp() { return state == EventLifecycle.REGISTRATION_OPEN; }
    public boolean acceptsCheckIn() { return state == EventLifecycle.CHECK_IN; }
    public boolean isTerminal() { return state == EventLifecycle.ARCHIVED; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value;
    }
}
