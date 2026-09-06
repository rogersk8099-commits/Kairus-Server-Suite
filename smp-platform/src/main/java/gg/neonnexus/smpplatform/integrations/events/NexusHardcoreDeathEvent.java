package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusHardcoreDeathEvent extends AbstractNexusEvent {
    private final UUID minecraftUuid;
    private final  String cause;
    private final  String worldId;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusHardcoreDeathEvent(UUID minecraftUuid,  String cause,  String worldId) {
        this.minecraftUuid = minecraftUuid;
        this.cause = cause;
        this.worldId = worldId;
    }
    public UUID minecraftUuid() { return minecraftUuid; }
    public  String cause() { return cause; }
    public  String worldId() { return worldId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
