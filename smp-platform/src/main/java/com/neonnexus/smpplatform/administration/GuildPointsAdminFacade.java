package com.neonnexus.smpplatform.administration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Locale;
import java.util.UUID;

public final class GuildPointsAdminFacade {
 private final JavaPlugin plugin;
 public GuildPointsAdminFacade(JavaPlugin plugin){this.plugin=plugin;}

 public JsonObject playerSummary(UUID target){
  OfflinePlayer p=Bukkit.getOfflinePlayer(target);
  JsonObject out=new JsonObject();out.addProperty("uuid",target.toString());
  out.addProperty("name",p.getName()==null?target.toString():p.getName());
  out.addProperty("online",p.isOnline());return out;
 }

 public String dispatch(Player actor,String area,String operation,String target,String value,String reason){
  String perm="smpplatform.admin."+area+"."+operation.toLowerCase(Locale.ROOT);
  if(!actor.hasPermission(perm)&&!actor.hasPermission("smpplatform.admin."+area+".*"))
   throw new SecurityException("Missing permission: "+perm);
  if(target==null||target.isBlank())throw new IllegalArgumentException("A target is required.");
  // Reuse the plugin's existing authoritative command handlers instead of duplicating persistence rules.
  String cmd;
  if(area.equals("points")){
   cmd=switch(operation){
    case "add" -> "points add "+target+" "+value+" "+reason;
    case "remove" -> "points remove "+target+" "+value+" "+reason;
    case "set" -> "points set "+target+" "+value+" "+reason;
    case "inspect" -> "points inspect "+target;
    default -> throw new IllegalArgumentException("Unsupported points operation.");
   };
  }else if(area.equals("guilds")){
   cmd=switch(operation){
    case "info" -> "guild info "+target;
    case "disband" -> "guild admin disband "+target+" "+reason;
    case "set-rank" -> "guild admin rank "+target+" "+value;
    case "remove-member" -> "guild admin remove "+target+" "+reason;
    default -> throw new IllegalArgumentException("Unsupported guild operation.");
   };
  }else throw new IllegalArgumentException("Unsupported administration area.");
  boolean accepted=Bukkit.dispatchCommand(actor,cmd);
  return accepted?"Command accepted by existing "+area+" service.":"The existing "+area+" handler rejected the operation.";
 }
}
