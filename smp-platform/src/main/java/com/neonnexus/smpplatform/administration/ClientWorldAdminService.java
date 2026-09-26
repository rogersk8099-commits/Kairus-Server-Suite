package com.neonnexus.smpplatform.administration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClientWorldAdminService {
    private final JavaPlugin plugin;
    public ClientWorldAdminService(JavaPlugin plugin){this.plugin=plugin;}

    public JsonObject summary(){
        JsonObject root=new JsonObject(); JsonArray worlds=new JsonArray();
        for(World world:Bukkit.getWorlds()){
            JsonObject row=new JsonObject();
            row.addProperty("name",world.getName());
            row.addProperty("environment",world.getEnvironment().name());
            row.addProperty("difficulty",world.getDifficulty().name());
            row.addProperty("pvp",world.getPVP());
            row.addProperty("players",world.getPlayers().size());
            row.addProperty("time",world.getTime());
            row.addProperty("storm",world.hasStorm());
            row.addProperty("thundering",world.isThundering());
            row.addProperty("spawnX",world.getSpawnLocation().getBlockX());
            row.addProperty("spawnY",world.getSpawnLocation().getBlockY());
            row.addProperty("spawnZ",world.getSpawnLocation().getBlockZ());
            row.addProperty("borderSize",world.getWorldBorder().getSize());
            row.addProperty("entities",world.getEntities().size());
            row.addProperty("livingEntities",world.getLivingEntities().size());
            worlds.add(row);
        }
        root.add("worldAdmin",worlds);return root;
    }

    public String execute(Player actor,String worldName,String action,String argument){
        String perm="smpplatform.admin.worlds."+action;
        if(!actor.hasPermission(perm)&&!actor.hasPermission("smpplatform.admin.worlds.*"))
            throw new SecurityException("Missing permission: "+perm);
        World world=Bukkit.getWorld(worldName);
        if(world==null)throw new IllegalArgumentException("World is not loaded.");
        return switch(action){
            case "difficulty" -> {world.setDifficulty(Difficulty.valueOf(argument.toUpperCase()));yield "Difficulty updated.";}
            case "pvp" -> {world.setPVP(Boolean.parseBoolean(argument));yield "PvP updated.";}
            case "time" -> {world.setTime(Long.parseLong(argument));yield "World time updated.";}
            case "weather-clear" -> {world.setStorm(false);world.setThundering(false);yield "Weather cleared.";}
            case "weather-rain" -> {world.setStorm(true);world.setThundering(false);yield "Rain enabled.";}
            case "weather-thunder" -> {world.setStorm(true);world.setThundering(true);yield "Thunder enabled.";}
            case "set-spawn" -> {world.setSpawnLocation(actor.getLocation());yield "World spawn moved to your location.";}
            case "border" -> {double size=Double.parseDouble(argument);if(size<32||size>60000000)throw new IllegalArgumentException("Border must be 32–60000000 blocks.");world.getWorldBorder().setSize(size);yield "World border updated.";}
            case "save" -> {world.save();yield "World saved.";}
            case "announce" -> {for(Player p:world.getPlayers())p.sendMessage("[World] "+argument);yield "Announcement sent to "+world.getPlayers().size()+" player(s).";}
            case "clear-items" -> {
                int count=0; for(var e:new java.util.ArrayList<>(world.getEntities())) if(e instanceof org.bukkit.entity.Item){e.remove();count++;}
                yield "Removed "+count+" dropped item(s).";
            }
            case "clear-mobs" -> {
                int count=0; for(var e:new java.util.ArrayList<>(world.getLivingEntities()))
                    if(!(e instanceof Player) && !(e instanceof org.bukkit.entity.ArmorStand)){e.remove();count++;}
                yield "Removed "+count+" mob(s).";
            }
            case "gamerule" -> {
                String[] bits=argument.split("=",2); if(bits.length!=2)throw new IllegalArgumentException("Use gamerule=value.");
                org.bukkit.GameRule<?> rule=org.bukkit.GameRule.getByName(bits[0]);
                if(rule==null)throw new IllegalArgumentException("Unknown gamerule.");
                if(rule.getType()==Boolean.class) world.setGameRule((org.bukkit.GameRule<Boolean>)rule,Boolean.parseBoolean(bits[1]));
                else if(rule.getType()==Integer.class) world.setGameRule((org.bukkit.GameRule<Integer>)rule,Integer.parseInt(bits[1]));
                else throw new IllegalArgumentException("Unsupported gamerule type.");
                yield "Gamerule updated.";
            }
            default -> throw new IllegalArgumentException("Unsupported world action.");
        };
    }
}
