package gg.neonnexus.smpplatform.lifecycle.audit;

import java.util.ArrayList;
import java.util.List;

/** Test/development implementation only; production wiring must use the platform audit_logs repository. */
public final class InMemoryAuditLogRepository implements AuditLogRepository {
    private final List<AuditEntry> entries = new ArrayList<>();

    @Override public synchronized void append(AuditEntry entry) { entries.add(entry); }
    public synchronized List<AuditEntry> entries() { return List.copyOf(entries); }
}
