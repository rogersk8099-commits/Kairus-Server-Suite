package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusWorldResetEvent extends AbstractNexusEvent {
    private final String worldId;
    private final  String resetType;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusWorldResetEvent(String worldId,  String resetType) {
        this.worldId = worldId;
        this.resetType = resetType;
    }
    public String worldId() { return worldId; }
    public  String resetType() { return resetType; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
