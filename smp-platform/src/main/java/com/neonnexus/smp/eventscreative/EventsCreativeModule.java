package com.neonnexus.smp.eventscreative;

import java.util.Objects;

public final class EventsCreativeModule {
    private final EventService events;
    private final CreativeService creative;
    private final EventsCreativeModuleHost host;

    public EventsCreativeModule(EventsCreativeModuleHost host) {
        this.host = Objects.requireNonNull(host, "host");
        this.events = new EventService(host.eventRepository(), host.outboxRepository(), host.auditLog(), host.clock());
        this.creative = new CreativeService(host.creativeRepository(), host.plotSquared(), host.worldEdit(), host.outboxRepository(), host.auditLog(), host.clock());
    }
    public EventService events() { return events; }
    public CreativeService creative() { return creative; }
    public void async(Runnable work) { host.executeAsync(work); }
}
