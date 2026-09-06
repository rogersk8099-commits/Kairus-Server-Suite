package network.neonnexus.smp.admin.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A fully attributable staff request. Values and reasons are never inferred from a GUI label. */
public record AdminActionRequest(
        UUID actorId,
        String actorName,
        UUID targetId,
        String targetName,
        AdminAction action,
        String value,
        String reason,
        Instant requestedAt,
        UUID correlationId
) {
    public AdminActionRequest {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorName, "actorName");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(correlationId, "correlationId");
        value = value == null ? "" : value.trim();
        reason = reason == null ? "" : reason.trim();
    }

    public AdminActionRequest withValue(String newValue) {
        return new AdminActionRequest(actorId, actorName, targetId, targetName, action, newValue, reason, requestedAt, correlationId);
    }

    public AdminActionRequest withReason(String newReason) {
        return new AdminActionRequest(actorId, actorName, targetId, targetName, action, value, newReason, requestedAt, correlationId);
    }
}
