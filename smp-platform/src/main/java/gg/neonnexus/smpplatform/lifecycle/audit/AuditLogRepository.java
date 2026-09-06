package gg.neonnexus.smpplatform.lifecycle.audit;

/** Boundary for PostgreSQL-backed audit_logs storage. Implementations must never silently discard writes. */
public interface AuditLogRepository {
    void append(AuditEntry entry);
}
