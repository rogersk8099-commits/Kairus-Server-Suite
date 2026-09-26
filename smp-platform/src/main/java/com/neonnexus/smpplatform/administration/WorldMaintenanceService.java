package com.neonnexus.smpplatform.administration;
import org.bukkit.entity.Player;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public final class WorldMaintenanceService {
 private final Set<String> worlds=ConcurrentHashMap.newKeySet();
 public boolean enabled(String world){return worlds.contains(world);}
 public boolean set(Player actor,String world,boolean enabled){
  if(!actor.hasPermission("smpplatform.admin.worlds.maintenance")&&!actor.hasPermission("smpplatform.admin.worlds.*"))
   throw new SecurityException("Missing permission: smpplatform.admin.worlds.maintenance");
  if(enabled)worlds.add(world);else worlds.remove(world);return enabled;
 }
}
