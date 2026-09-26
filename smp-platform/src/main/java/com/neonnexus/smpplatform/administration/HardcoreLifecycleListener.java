package com.neonnexus.smpplatform.administration;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HardcoreLifecycleListener implements Listener {
 private final JavaPlugin plugin; private final DataSource ds; private final HardcoreAdminRepository repo;
 private final String hardcoreWorld;
 private final ConcurrentHashMap<UUID,Long> enteredAt=new ConcurrentHashMap<>();
 public HardcoreLifecycleListener(JavaPlugin plugin,DataSource ds,HardcoreAdminRepository repo,String hardcoreWorld){
  this.plugin=plugin;this.ds=ds;this.repo=repo;this.hardcoreWorld=hardcoreWorld;
 }
 @EventHandler(priority=EventPriority.MONITOR)
 public void onDeath(PlayerDeathEvent event){
  Player p=event.getEntity(); if(!p.getWorld().getName().equalsIgnoreCase(hardcoreWorld))return;
  Location l=p.getLocation(); String cause=p.getLastDamageCause()==null?"UNKNOWN":p.getLastDamageCause().getCause().name();
  UUID killer=p.getKiller()==null?null:p.getKiller().getUniqueId();
  plugin.getServer().getScheduler().runTaskAsynchronously(plugin,()->{
   try(Connection c=ds.getConnection()){
    try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_hardcore_deaths(id,minecraft_uuid,cause,killer_uuid,x,y,z,survival_seconds,season,created_at) VALUES (?,?,?,?,?,?,?,?,?,NOW())")){
     ps.setObject(1,UUID.randomUUID());ps.setObject(2,p.getUniqueId());ps.setString(3,cause);
     if(killer==null)ps.setNull(4,Types.OTHER);else ps.setObject(4,killer);
     ps.setDouble(5,l.getX());ps.setDouble(6,l.getY());ps.setDouble(7,l.getZ());
     long entered=enteredAt.getOrDefault(p.getUniqueId(),System.currentTimeMillis());ps.setLong(8,Math.max(0,(System.currentTimeMillis()-entered)/1000L));
     ps.setString(9,plugin.getConfig().getString("season.current","CURRENT"));ps.executeUpdate();
    }
    repo.setState(p.getUniqueId(),"DEAD",p.getUniqueId(),"Hardcore death");
   }catch(Exception ex){plugin.getLogger().severe("Failed to record Hardcore death for "+p.getUniqueId()+": "+ex.getMessage());}
  });
 }
 @EventHandler(priority=EventPriority.HIGH)
 public void onRespawn(PlayerRespawnEvent event){
  Player p=event.getPlayer(); if(!p.getWorld().getName().equalsIgnoreCase(hardcoreWorld) && !event.getRespawnLocation().getWorld().getName().equalsIgnoreCase(hardcoreWorld))return;
  plugin.getServer().getScheduler().runTask(plugin,()->{p.setGameMode(GameMode.SPECTATOR);});
 }
 @EventHandler(priority=EventPriority.MONITOR)
 public void onWorldChange(PlayerChangedWorldEvent event){
  Player p=event.getPlayer(); if(!p.getWorld().getName().equalsIgnoreCase(hardcoreWorld)){enteredAt.remove(p.getUniqueId());return;}
  enteredAt.putIfAbsent(p.getUniqueId(),System.currentTimeMillis());
  plugin.getServer().getScheduler().runTaskAsynchronously(plugin,()->{
   try{
    var state=repo.lookup(p.getUniqueId()).get("state").getAsString();
    if(state.equals("DEAD")||state.equals("SPECTATING")||state.equals("LOCKED"))
      plugin.getServer().getScheduler().runTask(plugin,()->p.setGameMode(GameMode.SPECTATOR));
   }catch(Exception ignored){}
  });
 }
}
