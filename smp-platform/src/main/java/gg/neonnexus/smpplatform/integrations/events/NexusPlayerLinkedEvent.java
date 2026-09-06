package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusPlayerLinkedEvent extends AbstractNexusEvent {
    private final UUID minecraftUuid;
    private final  String platformUserId;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusPlayerLinkedEvent(UUID minecraftUuid,  String platformUserId) {
        this.minecraftUuid = minecraftUuid;
        this.platformUserId = platformUserId;
    }
    public UUID minecraftUuid() { return minecraftUuid; }
    public  String platformUserId() { return platformUserId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
