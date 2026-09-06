package gg.neonnexus.smpplatform.lifecycle.admin;

import gg.neonnexus.smpplatform.lifecycle.audit.AuditEntry;
import gg.neonnexus.smpplatform.lifecycle.audit.AuditLogRepository;
import gg.neonnexus.smpplatform.lifecycle.events.DurableEventOutbox;
import gg.neonnexus.smpplatform.lifecycle.hardcore.HardcoreLifecycleService;
import gg.neonnexus.smpplatform.lifecycle.hardcore.HardcorePlayer;
import gg.neonnexus.smpplatform.lifecycle.hardcore.HardcoreState;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Administrative state changes. Command/UI layers must call this only after ConfirmationTokenService confirms. */
public final class HardcoreAdminService {
    private final HardcoreLifecycleService lifecycle;
    private final AuditLogRepository audit;
    private final DurableEventOutbox events;
    public HardcoreAdminService(HardcoreLifecycleService lifecycle, AuditLogRepository audit, DurableEventOutbox events) {
        this.lifecycle = lifecycle; this.audit = audit; this.events = events;
    }
    public HardcorePlayer setStatus(UUID actor, UUID target, String season, HardcoreState state, String reason, String correlationId) {
        Instant now = Instant.now();
        HardcoreState before = lifecycle.state(target, season).map(HardcorePlayer::state).orElse(null);
        HardcorePlayer result = lifecycle.administrativeSetState(target, season, state, now);
        audit.append(new AuditEntry(UUID.randomUUID(), now, actor, "HARDCORE_STATUS_SET", target.toString(), "obsidian-gate",
                before == null ? "NONE" : before.name(), state.name(), reason, correlationId, Map.of("season", season)));
        events.publish("HARDCORE_STATUS_CHANGED", correlationId, Map.of("worldId", "obsidian-gate", "playerId", target.toString(), "status", state.name()));
        return result;
    }
    public HardcorePlayer revive(UUID actor, UUID target, String season, String reason, String correlationId) {
        return setStatus(actor, target, season, HardcoreState.ALIVE, reason, correlationId);
    }
    public HardcorePlayer eliminate(UUID actor, UUID target, String season, String reason, String correlationId) {
        return setStatus(actor, target, season, HardcoreState.LOCKED, reason, correlationId);
    }
}
