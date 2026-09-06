package com.neonnexus.smp.eventscreative;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for Neon Colosseum. Call only from an asynchronous persistence executor;
 * Bukkit world/teleport operations must return to the Paper scheduler after state is committed.
 */
public final class EventService {
    private final EventRepository events;
    private final OutboxRepository outbox;
    private final AuditLog audit;
    private final Clock clock;

    public EventService(EventRepository events, OutboxRepository outbox, AuditLog audit, Clock clock) {
        this.events = Objects.requireNonNull(events, "events");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ColosseumEvent create(UUID actorId, String slug, String name, EventType type, UUID arenaId,
                                 Instant scheduledAt, Instant checkInOpensAt, int registrationLimit, UUID correlationId) {
        return events.inTransaction(() -> {
            ArenaDefinition arena = events.findArena(arenaId).orElseThrow(() -> new IllegalArgumentException("Arena not found"));
            if (arena.gameType() != type && arena.gameType() != EventType.CUSTOM && type != EventType.CUSTOM) {
                throw new IllegalArgumentException("Arena game type does not support this event type");
            }
            if (registrationLimit > arena.maximumPlayers()) throw new IllegalArgumentException("Registration limit exceeds arena capacity");
            if (type == EventType.GAUNTLET && registrationLimit > 64) throw new IllegalArgumentException("Gauntlet may have no more than 64 participants");
            ColosseumEvent event = new ColosseumEvent(UUID.randomUUID(), slug, name, type, arenaId, actorId,
                EventLifecycle.DRAFT, scheduledAt, checkInOpensAt, registrationLimit, 0);
            events.saveEvent(event);
            audit(actorId, "EVENT_CREATED", "event", event.id().toString(), null, event.worldId(), correlationId);
            publish(event, PlatformEventType.EVENT_CREATED, Map.of("eventId", event.id().toString(), "slug", event.slug(), "name", event.name(), "type", event.type().name(), "state", event.state().name()));
            return event;
        });
    }

    public ColosseumEvent transition(UUID actorId, UUID eventId, EventLifecycle target, String reason, UUID correlationId) {
        return events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            if (target == EventLifecycle.STARTING) verifyStartingCapacity(event);
            EventLifecycle previous = event.state();
            event.transitionTo(target);
            events.saveEvent(event);
            audit(actorId, "EVENT_STATE_CHANGED", "event", event.id().toString(), reason, event.worldId(), correlationId);
            PlatformEventType outboxType = switch (target) {
                case LIVE -> PlatformEventType.EVENT_STARTED;
                case ARCHIVED -> PlatformEventType.EVENT_ARCHIVED;
                default -> PlatformEventType.EVENT_STATE_CHANGED;
            };
            publish(event, outboxType, Map.of("eventId", event.id().toString(), "from", previous.name(), "to", target.name(), "reason", nullable(reason)));
            return event;
        });
    }

    public EventRegistration rsvp(UUID playerId, UUID eventId, UUID correlationId) {
        return events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            if (!event.acceptsRsvp()) throw new IllegalStateException("RSVP is not open for this event");
            List<EventRegistration> registrations = events.registrations(eventId);
            if (registrations.stream().anyMatch(r -> r.playerId().equals(playerId) && r.state() != RegistrationState.WITHDRAWN)) {
                throw new IllegalStateException("Player already has an active registration");
            }
            long active = registrations.stream().filter(this::occupiesCapacity).count();
            EventRegistration registration;
            if (active < event.registrationLimit()) {
                registration = new EventRegistration(eventId, playerId, RegistrationState.REGISTERED, null, null, clock.instant(), null);
            } else {
                int nextPosition = registrations.stream().filter(r -> r.state() == RegistrationState.WAITLISTED).map(EventRegistration::queuePosition)
                    .max(Comparator.naturalOrder()).orElse(0) + 1;
                registration = new EventRegistration(eventId, playerId, RegistrationState.WAITLISTED, nextPosition, null, clock.instant(), null);
            }
            events.saveRegistration(registration);
            publish(event, PlatformEventType.EVENT_RSVP_CHANGED, Map.of("eventId", eventId.toString(), "playerId", playerId.toString(), "state", registration.state().name(), "waitlistPosition", nullable(registration.queuePosition())));
            return registration;
        });
    }

    public EventRegistration withdraw(UUID playerId, UUID eventId, UUID correlationId) {
        return events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            EventRegistration original = registrations(eventId).stream().filter(r -> r.playerId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No registration exists for player"));
            boolean freesCapacity = occupiesCapacity(original);
            EventRegistration withdrawn = original.withdraw();
            events.saveRegistration(withdrawn);
            if (freesCapacity) promoteOldestWaitlisted(event);
            publish(event, PlatformEventType.EVENT_RSVP_CHANGED, Map.of("eventId", eventId.toString(), "playerId", playerId.toString(), "state", withdrawn.state().name()));
            return withdrawn;
        });
    }

    public EventRegistration checkIn(UUID playerId, UUID eventId, UUID correlationId) {
        return events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            if (!event.acceptsCheckIn()) throw new IllegalStateException("Check-in is not open for this event");
            EventRegistration checkedIn = registrations(eventId).stream().filter(r -> r.playerId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No registration exists for player")).checkedIn(clock.instant());
            events.saveRegistration(checkedIn);
            publish(event, PlatformEventType.EVENT_CHECK_IN, Map.of("eventId", eventId.toString(), "playerId", playerId.toString(), "state", checkedIn.state().name()));
            return checkedIn;
        });
    }

    public EventRegistration assignTeam(UUID actorId, UUID eventId, UUID playerId, EventTeam team, UUID correlationId) {
        return events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            if (!team.eventId().equals(eventId)) throw new IllegalArgumentException("Team does not belong to event");
            if (event.state() != EventLifecycle.CHECK_IN && event.state() != EventLifecycle.STARTING) throw new IllegalStateException("Teams can only be assigned after check-in");
            EventRegistration assigned = registrations(eventId).stream().filter(r -> r.playerId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No registration exists for player")).assignTeam(team.id());
            events.saveTeam(team);
            events.saveRegistration(assigned);
            audit(actorId, "EVENT_TEAM_ASSIGNED", "event_registration", eventId + ":" + playerId, null, event.worldId(), correlationId);
            return assigned;
        });
    }

    public void recordResult(UUID actorId, UUID eventId, EventResult result, UUID correlationId) {
        events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            if (event.state() != EventLifecycle.LIVE) throw new IllegalStateException("Results can only be recorded while an event is live");
            boolean participant = registrations(eventId).stream().anyMatch(r -> r.playerId().equals(result.participantId()) && r.state() == RegistrationState.CHECKED_IN);
            if (!participant) throw new IllegalArgumentException("Result participant did not check in");
            events.saveResult(eventId, result);
            audit(actorId, "EVENT_RESULT_RECORDED", "event_result", eventId + ":" + result.participantId(), null, event.worldId(), correlationId);
            return null;
        });
    }

    public void finish(UUID actorId, UUID eventId, List<EventResult> results, UUID correlationId) {
        events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            if (event.state() != EventLifecycle.LIVE) throw new IllegalStateException("Only live events can finish");
            for (EventResult result : results) events.saveResult(eventId, result);
            event.transitionTo(EventLifecycle.FINISHED);
            events.saveEvent(event);
            audit(actorId, "EVENT_FINISHED", "event", eventId.toString(), null, event.worldId(), correlationId);
            publish(event, PlatformEventType.EVENT_RESULT, Map.of("eventId", eventId.toString(), "resultCount", results.size(), "state", event.state().name()));
            return null;
        });
    }

    public void defineReward(UUID actorId, UUID eventId, EventReward reward, UUID correlationId) {
        events.inTransaction(() -> {
            ColosseumEvent event = requireEvent(eventId);
            if (event.state() != EventLifecycle.FINISHED) throw new IllegalStateException("Rewards can only be defined after an event is finished");
            events.saveReward(eventId, reward);
            audit(actorId, "EVENT_REWARD_DEFINED", "event_reward", eventId.toString(), null, event.worldId(), correlationId);
            return null;
        });
    }

    private void verifyStartingCapacity(ColosseumEvent event) {
        ArenaDefinition arena = events.findArena(event.arenaId()).orElseThrow(() -> new IllegalStateException("Event arena disappeared"));
        long checkedIn = registrations(event.id()).stream().filter(r -> r.state() == RegistrationState.CHECKED_IN).count();
        if (checkedIn < arena.minimumPlayers()) throw new IllegalStateException("Not enough checked-in players to start: " + checkedIn + "/" + arena.minimumPlayers());
        if (checkedIn > event.registrationLimit() || checkedIn > arena.maximumPlayers()) throw new IllegalStateException("Checked-in participants exceed event capacity");
    }

    private void promoteOldestWaitlisted(ColosseumEvent event) {
        registrations(event.id()).stream().filter(r -> r.state() == RegistrationState.WAITLISTED)
            .min(Comparator.comparing(EventRegistration::queuePosition)).ifPresent(waitlisted -> events.saveRegistration(waitlisted.promote()));
    }
    private List<EventRegistration> registrations(UUID eventId) { return events.registrations(eventId); }
    private ColosseumEvent requireEvent(UUID id) { return events.find(id).orElseThrow(() -> new IllegalArgumentException("Event not found")); }
    private boolean occupiesCapacity(EventRegistration registration) { return EnumSet.of(RegistrationState.REGISTERED, RegistrationState.CHECKED_IN).contains(registration.state()); }
    private void audit(UUID actor, String action, String type, String target, String reason, String world, UUID correlation) {
        audit.record(new AuditEntry(actor, action, type, target, world, null, null, reason, correlation, clock.instant()));
    }
    private void publish(ColosseumEvent event, PlatformEventType type, Map<String, Object> payload) {
        outbox.enqueue(new PlatformOutboxEvent(UUID.randomUUID(), "event", event.id(), type,
            Set.of(PlatformDestination.WEBSITE, PlatformDestination.DISCORD), payload,
            type.name() + ":event:" + event.id() + ":r" + event.revision(), 0, clock.instant()));
    }
    private static Object nullable(Object value) { return value == null ? "" : value; } // Map.of rejects null; empty is intentional wire representation.
}
