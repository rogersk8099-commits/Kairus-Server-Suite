package network.neonnexus.smp.admin.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded operational fallback; replace or mirror with the phase-1 audit_logs repository in composition. */
public final class InMemoryAuditSink implements AdminActionDispatcher.AuditSink {
    private static final int LIMIT = 500;
    private final ArrayDeque<AdminActionDispatcher.AuditRecord> entries = new ArrayDeque<>();

    @Override public synchronized void write(AdminActionDispatcher.AuditRecord record) {
        entries.addFirst(record);
        while (entries.size() > LIMIT) entries.removeLast();
    }

    public synchronized List<AdminActionDispatcher.AuditRecord> recentFor(java.util.UUID targetId, int limit) {
        return entries.stream().filter(e -> e.targetId().equals(targetId)).limit(Math.max(0, limit)).toList();
    }

    public synchronized List<AdminActionDispatcher.AuditRecord> recent(int limit) {
        return new ArrayList<>(entries.stream().limit(Math.max(0, limit)).toList());
    }
}
