package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusMembershipChangeEvent extends AbstractNexusEvent {
    private final UUID minecraftUuid;
    private final  String previousTier;
    private final  String currentTier;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusMembershipChangeEvent(UUID minecraftUuid,  String previousTier,  String currentTier) {
        this.minecraftUuid = minecraftUuid;
        this.previousTier = previousTier;
        this.currentTier = currentTier;
    }
    public UUID minecraftUuid() { return minecraftUuid; }
    public  String previousTier() { return previousTier; }
    public  String currentTier() { return currentTier; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
