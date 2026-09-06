package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusGuildCreateEvent extends AbstractNexusEvent {
    private final UUID guildId;
    private final  UUID ownerUuid;
    private final  String guildName;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusGuildCreateEvent(UUID guildId,  UUID ownerUuid,  String guildName) {
        this.guildId = guildId;
        this.ownerUuid = ownerUuid;
        this.guildName = guildName;
    }
    public UUID guildId() { return guildId; }
    public  UUID ownerUuid() { return ownerUuid; }
    public  String guildName() { return guildName; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
