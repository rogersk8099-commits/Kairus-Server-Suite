package gg.neonnexus.smpplatform.integrations.events;

import org.bukkit.event.Event;
import java.time.Instant;
import java.util.UUID;

public abstract class AbstractNexusEvent extends Event {
    private final UUID correlationId; private final Instant occurredAt;
    protected AbstractNexusEvent() { super(false); correlationId = UUID.randomUUID(); occurredAt = Instant.now(); }
    public UUID correlationId() { return correlationId; } public Instant occurredAt() { return occurredAt; }
}
