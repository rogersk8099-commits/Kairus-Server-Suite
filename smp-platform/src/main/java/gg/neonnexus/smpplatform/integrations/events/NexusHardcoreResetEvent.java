package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusHardcoreResetEvent extends AbstractNexusEvent {
    private final UUID minecraftUuid;
    private final  String season;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusHardcoreResetEvent(UUID minecraftUuid,  String season) {
        this.minecraftUuid = minecraftUuid;
        this.season = season;
    }
    public UUID minecraftUuid() { return minecraftUuid; }
    public  String season() { return season; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
