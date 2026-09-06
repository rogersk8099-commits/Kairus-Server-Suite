package gg.neonnexus.smpplatform.lifecycle.quarry;

import gg.neonnexus.smpplatform.lifecycle.events.DurableEventOutbox;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Fail-closed reset orchestration. It never reaches save, backup, unload, or regeneration while
 * players remain in The Quarry, and it never regenerates without a verified backup.
 */
public final class QuarryResetCoordinator {
    private final QuarryGateway gateway;
    private final QuarryResetRepository repository;
    private final DurableEventOutbox events;

    public QuarryResetCoordinator(QuarryGateway gateway, QuarryResetRepository repository, DurableEventOutbox events) {
        this.gateway = gateway; this.repository = repository; this.events = events;
    }

    public synchronized QuarryResetSnapshot execute(String correlationId) {
        try {
            if (!gateway.isFallbackDestinationAvailable()) return abort(correlationId, "Fallback destination is unavailable");
            stage(correlationId, QuarryResetState.LOCKING_ENTRY, "Locking Quarry entry");
            if (!gateway.lockEntry()) return abort(correlationId, "Could not lock Quarry entry");
            stage(correlationId, QuarryResetState.EVACUATING, "Teleporting players to fallback");
            Collection<UUID> initialPlayers = new ArrayList<>(gateway.playersInQuarry());
            for (UUID playerId : initialPlayers) {
                if (!gateway.teleportToFallback(playerId)) return abort(correlationId, "Teleport failed for " + playerId);
            }
            stage(correlationId, QuarryResetState.VERIFYING_EMPTY, "Verifying Quarry has no players");
            Collection<UUID> remaining = gateway.playersInQuarry();
            if (!remaining.isEmpty()) return abort(correlationId, "Players remain in Quarry: " + remaining.size());
            stage(correlationId, QuarryResetState.SAVING, "Saving Quarry");
            if (!gateway.saveQuarry()) return abort(correlationId, "Quarry save failed");
            stage(correlationId, QuarryResetState.BACKING_UP, "Creating backup");
            BackupResult backup = gateway.createBackup();
            stage(correlationId, QuarryResetState.VERIFYING_BACKUP, "Verifying backup");
            if (backup == null || !backup.verified() || backup.location() == null) return abort(correlationId, "Backup verification failed: " + (backup == null ? "no result" : backup.detail()));
            events.publish("QUARRY_BACKUP_VERIFIED", correlationId, Map.of("worldId", "quarry", "backup", backup.location().toString()));
            stage(correlationId, QuarryResetState.VERIFYING_EMPTY, "Re-verifying Quarry has no players before destructive work");
            if (!gateway.playersInQuarry().isEmpty()) return abort(correlationId, "Players appeared in Quarry before unload");
            stage(correlationId, QuarryResetState.UNLOADING, "Unloading Quarry");
            if (!gateway.unloadQuarry()) return fail(correlationId, "Quarry unload failed after verified backup");
            stage(correlationId, QuarryResetState.REGENERATING, "Regenerating Quarry");
            if (!gateway.regenerateQuarry()) return fail(correlationId, "Quarry regeneration failed after unload");
            stage(correlationId, QuarryResetState.LOADING, "Loading regenerated Quarry");
            if (!gateway.loadQuarry()) return fail(correlationId, "Quarry load failed");
            stage(correlationId, QuarryResetState.VALIDATING, "Validating regenerated Quarry");
            if (!gateway.validateQuarry()) return fail(correlationId, "Quarry validation failed; entry remains locked");
            stage(correlationId, QuarryResetState.OPENING, "Opening Quarry");
            gateway.unlockEntry();
            return stage(correlationId, QuarryResetState.COMPLETED, "Quarry reset complete");
        } catch (RuntimeException exception) {
            return fail(correlationId, "Unexpected reset failure: " + exception.getClass().getSimpleName());
        }
    }

    private QuarryResetSnapshot abort(String correlationId, String detail) {
        gateway.unlockEntry(); // no destructive step occurred before every abort condition
        return stage(correlationId, QuarryResetState.ABORTED, detail);
    }
    private QuarryResetSnapshot fail(String correlationId, String detail) { return stage(correlationId, QuarryResetState.FAILED, detail); }
    private QuarryResetSnapshot stage(String correlationId, QuarryResetState state, String detail) {
        QuarryResetSnapshot snapshot = new QuarryResetSnapshot(correlationId, state, Instant.now(), detail);
        repository.save(snapshot);
        events.publish("QUARRY_RESET_" + state.name(), correlationId, Map.of("worldId", "quarry", "state", state.name(), "detail", detail));
        return snapshot;
    }
}
