package network.neonnexus.smp.admin.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The only path from an admin interaction to an executor. It makes destructive
 * execution impossible before a current confirmation has been consumed.
 */
public final class AdminActionDispatcher {
    private final PermissionRouter permissions;
    private final ConfirmationService confirmations;
    private final ActionExecutor executor;
    private final AuditSink audit;
    private final Clock clock;

    public AdminActionDispatcher(PermissionRouter permissions, ConfirmationService confirmations,
                                 ActionExecutor executor, AuditSink audit, Clock clock) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DispatchOutcome request(PermissionRouter.PermissionSubject actor, AdminActionRequest request) {
        if (!permissions.canPerform(actor, request.action())) {
            audit.write(AuditRecord.rejected(request, "Missing permission " + request.action().permission(), clock.instant()));
            return DispatchOutcome.denied("You do not have permission for " + request.action().displayName() + ".");
        }
        if (request.action().needsReason() && request.reason().isBlank()) {
            audit.write(AuditRecord.rejected(request, "Reason required", clock.instant()));
            return DispatchOutcome.failed("A reason is required for " + request.action().displayName() + ".");
        }
        if (request.action().needsValue() && request.value().isBlank()) {
            audit.write(AuditRecord.rejected(request, "Value required", clock.instant()));
            return DispatchOutcome.failed("A value is required for " + request.action().displayName() + ".");
        }
        if (request.action().destructive()) {
            ConfirmationService.PendingConfirmation pending = confirmations.create(request);
            audit.write(AuditRecord.confirmationRequested(request, pending.id(), pending.expiresAt(), clock.instant()));
            return DispatchOutcome.confirmationRequired(pending.id(), pending.expiresAt(), "Confirm " + request.action().displayName() + ".");
        }
        return execute(request);
    }

    public DispatchOutcome confirm(PermissionRouter.PermissionSubject actor, UUID actorId, UUID confirmationId) {
        ConfirmationService.Consumption consumed = confirmations.consume(actorId, confirmationId);
        if (consumed.status() != ConfirmationService.Status.CONSUMED) {
            return DispatchOutcome.expired("Confirmation is no longer valid or does not belong to you.");
        }
        AdminActionRequest request = consumed.request();
        if (!permissions.canPerform(actor, request.action())) {
            audit.write(AuditRecord.rejected(request, "Permission changed before confirmation", clock.instant()));
            return DispatchOutcome.denied("Permission changed; action was not executed.");
        }
        return execute(request);
    }

    public void cancel(UUID actorId) { confirmations.cancel(actorId); }

    private DispatchOutcome execute(AdminActionRequest request) {
        ActionResult result = executor.execute(request);
        audit.write(result.success()
                ? AuditRecord.executed(request, result.detail(), clock.instant())
                : AuditRecord.rejected(request, result.detail(), clock.instant()));
        return result.success() ? DispatchOutcome.executed(result.detail()) : DispatchOutcome.failed(result.detail());
    }

    @FunctionalInterface public interface ActionExecutor { ActionResult execute(AdminActionRequest request); }
    public record ActionResult(boolean success, String detail) {
        public static ActionResult success(String detail) { return new ActionResult(true, detail); }
        public static ActionResult failure(String detail) { return new ActionResult(false, detail); }
    }
    public record DispatchOutcome(Status status, UUID confirmationId, Instant expiresAt, String message) {
        public static DispatchOutcome executed(String message) { return new DispatchOutcome(Status.EXECUTED, null, null, message); }
        public static DispatchOutcome confirmationRequired(UUID id, Instant at, String message) { return new DispatchOutcome(Status.CONFIRMATION_REQUIRED, id, at, message); }
        public static DispatchOutcome denied(String message) { return new DispatchOutcome(Status.DENIED, null, null, message); }
        public static DispatchOutcome failed(String message) { return new DispatchOutcome(Status.FAILED, null, null, message); }
        public static DispatchOutcome expired(String message) { return new DispatchOutcome(Status.EXPIRED, null, null, message); }
    }
    public enum Status { EXECUTED, CONFIRMATION_REQUIRED, DENIED, FAILED, EXPIRED }

    public interface AuditSink { void write(AuditRecord record); }
    public record AuditRecord(UUID correlationId, UUID actorId, String actorName, UUID targetId, String targetName,
                              String action, String outcome, String detail, String reason, Instant timestamp) {
        static AuditRecord executed(AdminActionRequest r, String detail, Instant at) { return record(r, "EXECUTED", detail, at); }
        static AuditRecord rejected(AdminActionRequest r, String detail, Instant at) { return record(r, "REJECTED", detail, at); }
        static AuditRecord confirmationRequested(AdminActionRequest r, UUID id, Instant expiry, Instant at) {
            return record(r, "CONFIRMATION_REQUESTED", "id=" + id + ", expires=" + expiry, at);
        }
        private static AuditRecord record(AdminActionRequest r, String outcome, String detail, Instant at) {
            return new AuditRecord(r.correlationId(), r.actorId(), r.actorName(), r.targetId(), r.targetName(), r.action().name(), outcome, detail, r.reason(), at);
        }
    }
}
