package gg.neonnexus.smpplatform.lifecycle.hardcore;

import gg.neonnexus.smpplatform.lifecycle.events.DurableEventOutbox;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Application service for the custom Obsidian Gate one-life lifecycle. */
public final class HardcoreLifecycleService {
    private final HardcoreRepository repository;
    private final HardcoreStatisticsRepository statistics;
    private final DurableEventOutbox events;

    public HardcoreLifecycleService(HardcoreRepository repository, HardcoreStatisticsRepository statistics, DurableEventOutbox events) {
        this.repository = repository;
        this.statistics = statistics;
        this.events = events;
    }

    public HardcorePlayer registerIfAbsent(UUID playerId, String season, Instant now) {
        return repository.find(playerId, season).orElseGet(() -> {
            HardcorePlayer created = new HardcorePlayer(playerId, season, HardcoreState.ALIVE, now, now);
            repository.savePlayer(created);
            statistics.save(playerId, season, HardcoreStatistics.empty());
            return created;
        });
    }

    /** Persists death evidence before the spectating enforcement state is published. */
    public HardcorePlayer recordDeath(DeathCapture capture, String correlationId) {
        HardcorePlayer current = registerIfAbsent(capture.playerId(), capture.season(), capture.occurredAt());
        if (current.state() != HardcoreState.ALIVE) return current; // Avoid double firing by plugins/event replays.
        HardcorePlayer dead = HardcoreStateMachine.transition(current, HardcoreState.DEAD, capture.occurredAt());
        HardcoreStatistics snapshot = capture.statistics().withDeath(capture.survivalTime().toSeconds());
        HardcoreDeathRecord death = new HardcoreDeathRecord(UUID.randomUUID(), capture.playerId(), capture.season(),
                capture.cause(), capture.killerId(), capture.killerName(), capture.position(), capture.survivalTime(), snapshot, capture.occurredAt());
        repository.savePlayer(dead);
        statistics.save(capture.playerId(), capture.season(), snapshot);
        repository.appendDeath(death);
        events.publish("HARDCORE_DEATH", correlationId, Map.of("worldId", "obsidian-gate", "playerId", capture.playerId().toString(),
                "deathId", death.id().toString(), "cause", safe(capture.cause()), "state", HardcoreState.DEAD.name()));
        HardcorePlayer spectating = HardcoreStateMachine.transition(dead, HardcoreState.SPECTATING, capture.occurredAt());
        repository.savePlayer(spectating);
        events.publish("HARDCORE_SPECTATING", correlationId, Map.of("worldId", "obsidian-gate", "playerId", capture.playerId().toString()));
        return spectating;
    }

    /** Opens a configured reset window and advances every spectator through RESET_ELIGIBLE before normal play resumes. */
    public List<HardcorePlayer> openResetWindow(String season, Instant now, boolean automaticallyRevive, String correlationId) {
        List<HardcorePlayer> spectators = repository.findByState(season, HardcoreState.SPECTATING);
        java.util.ArrayList<HardcorePlayer> result = new java.util.ArrayList<>();
        for (HardcorePlayer spectator : spectators) {
            HardcorePlayer eligible = HardcoreStateMachine.transition(spectator, HardcoreState.RESET_ELIGIBLE, now);
            repository.savePlayer(eligible);
            events.publish("HARDCORE_RESET_ELIGIBLE", correlationId, Map.of("worldId", "obsidian-gate", "playerId", spectator.playerId().toString()));
            if (automaticallyRevive) {
                HardcorePlayer alive = new HardcorePlayer(eligible.playerId(), eligible.season(), HardcoreState.ALIVE, now, now);
                repository.savePlayer(alive);
                events.publish("HARDCORE_RESET_REVIVED", correlationId, Map.of("worldId", "obsidian-gate", "playerId", spectator.playerId().toString()));
                result.add(alive);
            } else result.add(eligible);
        }
        events.publish("HARDCORE_RESET_WINDOW_OPENED", correlationId, Map.of("worldId", "obsidian-gate", "season", season, "affectedPlayers", Integer.toString(result.size())));
        return List.copyOf(result);
    }

    public Optional<HardcorePlayer> state(UUID playerId, String season) { return repository.find(playerId, season); }

    /** Package-facing controlled override; callers must be confirmation-gated and audit logged. */
    public HardcorePlayer administrativeSetState(UUID playerId, String season, HardcoreState target, Instant now) {
        HardcorePlayer current = registerIfAbsent(playerId, season, now);
        HardcorePlayer changed = new HardcorePlayer(playerId, season, target, now, target == HardcoreState.ALIVE ? now : current.lifeStartedAt());
        repository.savePlayer(changed);
        return changed;
    }

    private static String safe(String value) { return value == null ? "unknown" : value; }
}
