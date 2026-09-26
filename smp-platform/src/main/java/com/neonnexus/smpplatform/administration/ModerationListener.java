package com.neonnexus.smpplatform.administration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerationListener implements Listener {
    private final ConcurrentHashMap<UUID,Instant> muted=new ConcurrentHashMap<>();
    public void mute(UUID id,Instant until){muted.put(id,until==null?Instant.MAX:until);}
    public void unmute(UUID id){muted.remove(id);}
    @EventHandler public void chat(AsyncPlayerChatEvent event){
        Instant until=muted.get(event.getPlayer().getUniqueId());
        if(until==null)return;
        if(until!=Instant.MAX && Instant.now().isAfter(until)){muted.remove(event.getPlayer().getUniqueId());return;}
        event.setCancelled(true);event.getPlayer().sendMessage("You are muted.");
    }
}
