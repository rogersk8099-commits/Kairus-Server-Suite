package com.neonnexus.smp.eventscreative;

import java.time.Clock;

/**
 * Parent SMPPlatform supplies implementations backed by Hikari/PostgreSQL, async execution, and its audit service.
 * Register this as a Bukkit service before enabling this module. It intentionally contains no database credentials.
 */
public interface EventsCreativeModuleHost {
    EventRepository eventRepository();
    CreativeRepository creativeRepository();
    OutboxRepository outboxRepository();
    AuditLog auditLog();
    PlotSquaredAdapter plotSquared();
    WorldEditAdapter worldEdit();
    Clock clock();
    void executeAsync(Runnable work);
}
