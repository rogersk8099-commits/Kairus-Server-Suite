package gg.neonnexus.smpplatform.integrations.events;

import java.util.UUID;
import org.bukkit.event.HandlerList;

public final class NexusPointsChangeEvent extends AbstractNexusEvent {
    private final UUID minecraftUuid;
    private final  String currency;
    private final  long amount;
    private final  String reason;
    private static final HandlerList HANDLERS = new HandlerList();
    public NexusPointsChangeEvent(UUID minecraftUuid,  String currency,  long amount,  String reason) {
        this.minecraftUuid = minecraftUuid;
        this.currency = currency;
        this.amount = amount;
        this.reason = reason;
    }
    public UUID minecraftUuid() { return minecraftUuid; }
    public  String currency() { return currency; }
    public  long amount() { return amount; }
    public  String reason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
