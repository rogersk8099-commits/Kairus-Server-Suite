package com.neonnexus.smp.eventscreative;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventRegistration(
    UUID eventId,
    UUID playerId,
    RegistrationState state,
    Integer queuePosition,
    UUID teamId,
    Instant registeredAt,
    Instant checkedInAt
) {
    public EventRegistration {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(registeredAt, "registeredAt");
        if (state == RegistrationState.WAITLISTED && (queuePosition == null || queuePosition < 1)) throw new IllegalArgumentException("Waitlisted registration requires a positive position");
        if (state != RegistrationState.WAITLISTED && queuePosition != null) throw new IllegalArgumentException("Only waitlisted registrations have a queue position");
    }

    public EventRegistration checkedIn(Instant at) {
        if (state != RegistrationState.REGISTERED) throw new IllegalStateException("Only registered players can check in");
        return new EventRegistration(eventId, playerId, RegistrationState.CHECKED_IN, null, teamId, registeredAt, Objects.requireNonNull(at));
    }

    public EventRegistration promote() {
        if (state != RegistrationState.WAITLISTED) throw new IllegalStateException("Only waitlisted registrations can be promoted");
        return new EventRegistration(eventId, playerId, RegistrationState.REGISTERED, null, teamId, registeredAt, null);
    }

    public EventRegistration withdraw() {
        if (state == RegistrationState.CHECKED_IN || state == RegistrationState.COMPLETED || state == RegistrationState.DISQUALIFIED) {
            throw new IllegalStateException("Registration cannot be withdrawn in state " + state);
        }
        return new EventRegistration(eventId, playerId, RegistrationState.WITHDRAWN, null, teamId, registeredAt, checkedInAt);
    }

    public EventRegistration assignTeam(UUID assignedTeamId) {
        if (state != RegistrationState.CHECKED_IN) throw new IllegalStateException("Only checked-in participants can be placed on a team");
        return new EventRegistration(eventId, playerId, state, null, Objects.requireNonNull(assignedTeamId), registeredAt, checkedInAt);
    }
}
