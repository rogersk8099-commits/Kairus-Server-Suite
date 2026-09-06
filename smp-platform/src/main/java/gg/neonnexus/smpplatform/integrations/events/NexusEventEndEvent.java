package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusEventEndEvent extends AbstractNexusEvent {
    private final UUID eventId;
    private final  String eventType;
    private final  String worldId;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusEventEndEvent(UUID eventId,  String eventType,  String worldId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.worldId = worldId;
    }
    public UUID eventId() { return eventId; }
    public  String eventType() { return eventType; }
    public  String worldId() { return worldId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
