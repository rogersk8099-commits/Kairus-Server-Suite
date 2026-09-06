package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusGuildLeaveEvent extends AbstractNexusEvent {
    private final UUID guildId;
    private final  UUID minecraftUuid;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusGuildLeaveEvent(UUID guildId,  UUID minecraftUuid) {
        this.guildId = guildId;
        this.minecraftUuid = minecraftUuid;
    }
    public UUID guildId() { return guildId; }
    public  UUID minecraftUuid() { return minecraftUuid; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
