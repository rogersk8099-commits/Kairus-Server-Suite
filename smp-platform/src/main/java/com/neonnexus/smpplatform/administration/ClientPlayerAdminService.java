package com.neonnexus.smpplatform.administration;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public final class ClientPlayerAdminService {
    private final JavaPlugin plugin;
    public ClientPlayerAdminService(JavaPlugin plugin){this.plugin=plugin;}

    public JsonObject profile(UUID targetId) {
        JsonObject out=new JsonObject();
        OfflinePlayer offline=Bukkit.getOfflinePlayer(targetId);
        out.addProperty("uuid",targetId.toString());
        out.addProperty("name",offline.getName()==null?targetId.toString():offline.getName());
        out.addProperty("online",offline.isOnline());
        if(offline.getLastSeen()>0) out.addProperty("lastSeen",offline.getLastSeen());
        Player target=offline.getPlayer();
        if(target!=null){
            out.addProperty("world",target.getWorld().getName());
            out.addProperty("health",target.getHealth());
            out.addProperty("maxHealth",target.getMaxHealth());
            out.addProperty("food",target.getFoodLevel());
            out.addProperty("level",target.getLevel());
            out.addProperty("gameMode",target.getGameMode().name());
            out.addProperty("x",target.getLocation().getBlockX());
            out.addProperty("y",target.getLocation().getBlockY());
            out.addProperty("z",target.getLocation().getBlockZ());
        }
        return out;
    }

    public String execute(Player actor, UUID targetId, String action, String argument) {
        String permission="smpplatform.admin.players."+action.toLowerCase(Locale.ROOT).replace('_','-');
        if(!actor.hasPermission(permission) && !actor.hasPermission("smpplatform.admin.players.*"))
            throw new SecurityException("Missing permission: "+permission);
        Player target=Bukkit.getPlayer(targetId);
        switch(action.toLowerCase(Locale.ROOT)){
            case "bring" -> { requireOnline(target); target.teleport(actor.getLocation()); return "Brought "+target.getName()+" to you."; }
            case "teleport" -> { requireOnline(target); actor.teleport(target.getLocation()); return "Teleported to "+target.getName()+"."; }
            case "spawn" -> { requireOnline(target); Location spawn=target.getWorld().getSpawnLocation(); target.teleport(spawn); return "Sent "+target.getName()+" to spawn."; }
            case "heal" -> { requireOnline(target); target.setHealth(target.getMaxHealth()); return "Healed "+target.getName()+"."; }
            case "feed" -> { requireOnline(target); target.setFoodLevel(20); target.setSaturation(20f); return "Fed "+target.getName()+"."; }
            case "clear-effects" -> { requireOnline(target); target.getActivePotionEffects().forEach(e->target.removePotionEffect(e.getType())); return "Cleared effects for "+target.getName()+"."; }
            case "health" -> { requireOnline(target); double value=Math.max(0.5,Math.min(target.getMaxHealth(),Double.parseDouble(argument))); target.setHealth(value); return "Set health for "+target.getName()+"."; }
            case "hunger" -> { requireOnline(target); int value=Math.max(0,Math.min(20,Integer.parseInt(argument))); target.setFoodLevel(value); return "Set hunger for "+target.getName()+"."; }
            case "xp-level" -> { requireOnline(target); int value=Math.max(0,Integer.parseInt(argument)); target.setLevel(value); return "Set XP level for "+target.getName()+"."; }
            case "freeze" -> { requireOnline(target); target.setFreezeTicks(target.getMaxFreezeTicks()); target.setWalkSpeed(0f); return "Froze "+target.getName()+"."; }
            case "unfreeze" -> { requireOnline(target); target.setFreezeTicks(0); target.setWalkSpeed(0.2f); return "Unfroze "+target.getName()+"."; }
            case "clear-inventory" -> { requireOnline(target); target.getInventory().clear(); return "Cleared inventory for "+target.getName()+"."; }
            case "gamemode" -> {
                requireOnline(target);
                GameMode mode=GameMode.valueOf(argument.toUpperCase(Locale.ROOT));
                target.setGameMode(mode); return "Set "+target.getName()+" to "+mode.name()+".";
            }
            case "world" -> {
                requireOnline(target);
                World world=Bukkit.getWorld(argument);
                if(world==null) throw new IllegalArgumentException("World is not loaded.");
                target.teleport(world.getSpawnLocation()); return "Sent "+target.getName()+" to "+world.getName()+".";
            }
            case "kick" -> { requireOnline(target); target.kickPlayer(argument==null||argument.isBlank()?"Removed by staff.":argument); return "Player kicked."; }
            default -> throw new IllegalArgumentException("Unsupported player action.");
        }
    }

    public JsonObject inventorySlot(UUID targetId, boolean ender, int slot) {
        Player target=Bukkit.getPlayer(targetId); requireOnline(target);
        org.bukkit.inventory.Inventory inventory=ender?target.getEnderChest():target.getInventory();
        if(slot<0 || slot>=inventory.getSize()) throw new IllegalArgumentException("Invalid inventory slot.");
        var item=inventory.getItem(slot); JsonObject out=new JsonObject();
        out.addProperty("slot",slot); out.addProperty("kind",ender?"ENDER_CHEST":"INVENTORY");
        if(item==null || item.getType().isAir()){out.addProperty("empty",true);return out;}
        out.addProperty("empty",false);out.addProperty("type",item.getType().getKey().asString());out.addProperty("amount",item.getAmount());
        if(item.hasItemMeta()){
            var meta=item.getItemMeta();
            if(meta.hasDisplayName())out.addProperty("name",meta.getDisplayName());
            out.addProperty("hasLore",meta.hasLore());
            out.addProperty("enchants",meta.getEnchants().size());
            out.addProperty("customModelData",meta.hasCustomModelData()?meta.getCustomModelData():0);
        }
        out.addProperty("fingerprint",java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        return out;
    }

    public String removeInventorySlot(Player actor, UUID targetId, boolean ender, int slot, String expectedFingerprint) {
        String permission=ender?"smpplatform.admin.players.enderchest.edit":"smpplatform.admin.players.inventory.edit";
        if(!actor.hasPermission(permission)&&!actor.hasPermission("smpplatform.admin.players.*"))throw new SecurityException("Missing permission: "+permission);
        Player target=Bukkit.getPlayer(targetId); requireOnline(target);
        org.bukkit.inventory.Inventory inventory=ender?target.getEnderChest():target.getInventory();
        if(slot<0||slot>=inventory.getSize())throw new IllegalArgumentException("Invalid inventory slot.");
        var current=inventory.getItem(slot);
        if(current==null||current.getType().isAir())throw new IllegalStateException("That slot is already empty.");
        String actual=java.util.Base64.getEncoder().encodeToString(current.serializeAsBytes());
        if(expectedFingerprint==null||!actual.equals(expectedFingerprint))throw new IllegalStateException("The slot changed since it was inspected. Refresh before editing.");
        inventory.setItem(slot,null);
        return "Removed item from "+(ender?"Ender Chest":"inventory")+" slot "+slot+".";
    }

    public String moveInventorySlot(Player actor, UUID targetId, boolean ender, int from, int to, String expectedFingerprint) {
        String permission=ender?"smpplatform.admin.players.enderchest.edit":"smpplatform.admin.players.inventory.edit";
        if(!actor.hasPermission(permission)&&!actor.hasPermission("smpplatform.admin.players.*"))throw new SecurityException("Missing permission: "+permission);
        Player target=Bukkit.getPlayer(targetId); requireOnline(target);
        org.bukkit.inventory.Inventory inventory=ender?target.getEnderChest():target.getInventory();
        if(from<0||to<0||from>=inventory.getSize()||to>=inventory.getSize())throw new IllegalArgumentException("Invalid inventory slot.");
        var current=inventory.getItem(from);
        if(current==null||current.getType().isAir())throw new IllegalStateException("Source slot is empty.");
        String actual=java.util.Base64.getEncoder().encodeToString(current.serializeAsBytes());
        if(expectedFingerprint==null||!actual.equals(expectedFingerprint))throw new IllegalStateException("The source slot changed since it was inspected.");
        var destination=inventory.getItem(to);
        inventory.setItem(to,current); inventory.setItem(from,destination);
        return "Moved inventory slot "+from+" to "+to+".";
    }

    public JsonObject inventory(UUID targetId, boolean ender) {
        Player target=Bukkit.getPlayer(targetId); requireOnline(target);
        JsonObject out=new JsonObject();
        com.google.gson.JsonArray items=new com.google.gson.JsonArray();
        org.bukkit.inventory.Inventory inventory=ender?target.getEnderChest():target.getInventory();
        for(int slot=0;slot<inventory.getSize();slot++){
            var item=inventory.getItem(slot);
            if(item==null || item.getType().isAir()) continue;
            JsonObject row=new JsonObject(); row.addProperty("slot",slot);
            row.addProperty("type",item.getType().getKey().asString()); row.addProperty("amount",item.getAmount());
            if(item.hasItemMeta() && item.getItemMeta().hasDisplayName()) row.addProperty("name",item.getItemMeta().getDisplayName());
            row.addProperty("fingerprint",java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            items.add(row);
        }
        out.addProperty("target",targetId.toString()); out.addProperty("kind",ender?"ENDER_CHEST":"INVENTORY"); out.add("items",items); return out;
    }

    private static void requireOnline(Player player){
        if(player==null) throw new IllegalStateException("That action requires the player to be online.");
    }
}
