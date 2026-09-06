package com.neonnexus.smp.eventscreative;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implement with PostgreSQL prepared statements inside a platform transaction boundary. Never invoke on Paper's main thread. */
interface EventRepository {
    Optional<ColosseumEvent> find(UUID eventId);
    Optional<ArenaDefinition> findArena(UUID arenaId);
    List<EventRegistration> registrations(UUID eventId);
    void saveEvent(ColosseumEvent event);
    void saveRegistration(EventRegistration registration);
    void saveTeam(EventTeam team);
    void saveRound(EventRound round);
    void saveMatch(EventMatch match);
    void saveResult(UUID eventId, EventResult result);
    void saveReward(UUID eventId, EventReward reward);
    <T> T inTransaction(TransactionalWork<T> work);
    @FunctionalInterface interface TransactionalWork<T> { T run(); }
}

interface CreativeRepository {
    Optional<BuildSubmission> findSubmission(UUID submissionId);
    List<BuildSubmission> reviewQueue(int limit);
    void saveSubmission(BuildSubmission submission);
    void saveFeaturedBuild(FeaturedBuild featuredBuild);
    <T> T inTransaction(EventRepository.TransactionalWork<T> work);
}

interface AuditLog {
    void record(AuditEntry entry);
}
record AuditEntry(UUID actorId, String action, String targetType, String targetId, String worldId,
                  String beforeState, String afterState, String reason, UUID correlationId, Instant createdAt) { }
