package com.neonnexus.smp.eventscreative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

enum RoundState { PENDING, LIVE, COMPLETE, CANCELLED }
enum MatchState { PENDING, LIVE, COMPLETE, FORFEIT, CANCELLED }

record EventTeam(UUID id, UUID eventId, String name, UUID captainId, List<UUID> members) {
    EventTeam {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(eventId, "eventId"); Objects.requireNonNull(captainId, "captainId");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Team name is required");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (!members.contains(captainId)) throw new IllegalArgumentException("Captain must be a team member");
    }
}

record EventRound(UUID id, UUID eventId, int number, RoundState state, Instant startedAt, Instant completedAt) {
    EventRound {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(eventId, "eventId"); Objects.requireNonNull(state, "state");
        if (number < 1) throw new IllegalArgumentException("Round number must be positive");
    }
}

record EventMatch(UUID id, UUID roundId, UUID sideA, UUID sideB, UUID winner, int scoreA, int scoreB, MatchState state) {
    EventMatch {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(roundId, "roundId"); Objects.requireNonNull(state, "state");
        if (scoreA < 0 || scoreB < 0) throw new IllegalArgumentException("Scores cannot be negative");
        if (winner != null && !winner.equals(sideA) && !winner.equals(sideB)) throw new IllegalArgumentException("Winner must be a match side");
    }
}

/** Bracket generation remains explicit: callers choose teams/participants and persist the generated rounds/matches. */
final class SingleEliminationBracket {
    private SingleEliminationBracket() { }

    static List<EventMatch> firstRound(UUID roundId, List<UUID> seededSides) {
        if (seededSides.size() < 2 || Integer.bitCount(seededSides.size()) != 1) {
            throw new IllegalArgumentException("Single-elimination brackets require a power-of-two number of sides");
        }
        return java.util.stream.IntStream.range(0, seededSides.size() / 2)
            .mapToObj(i -> new EventMatch(UUID.randomUUID(), roundId, seededSides.get(i * 2), seededSides.get(i * 2 + 1), null, 0, 0, MatchState.PENDING))
            .toList();
    }
}
