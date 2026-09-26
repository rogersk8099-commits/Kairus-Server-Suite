package com.neonnexus.smpplatform.administration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminConfirmations {
    public record Pending(UUID target,String action,String argument,Instant expiresAt){}
    private final ConcurrentHashMap<UUID,Pending> pending=new ConcurrentHashMap<>();
    public void prepare(UUID actor,UUID target,String action,String argument){
        pending.put(actor,new Pending(target,action,argument,Instant.now().plusSeconds(30)));
    }
    public Pending consume(UUID actor,UUID target,String action){
        Pending p=pending.remove(actor);
        if(p==null||!p.target().equals(target)||!p.action().equals(action)||Instant.now().isAfter(p.expiresAt()))return null;
        return p;
    }
}
