package com.neonnexus.smpplatform.auction;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionConfirmations {
    public record Pending(String action, String payload, Instant expiresAt) {}
    private final ConcurrentHashMap<UUID,Pending> pending=new ConcurrentHashMap<>();
    public void put(UUID player,String action,String payload){pending.put(player,new Pending(action,payload,Instant.now().plusSeconds(30)));}
    public Pending consume(UUID player,String action){
        Pending p=pending.remove(player);
        if(p==null || !p.action().equals(action) || Instant.now().isAfter(p.expiresAt())) return null;
        return p;
    }
}
