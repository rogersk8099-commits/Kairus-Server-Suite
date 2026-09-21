package com.neonnexus.smpplatform.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neonnexus.smpplatform.SMPPlatform;
import com.neonnexus.smpplatform.world.WorldDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Permission-checked server gateway for the optional Fabric UI. No client request is trusted as authority. */
public final class KairuClientGateway {
    public static final String PREFIX = "KAIRU_ADMIN_V2:";
    private final SMPPlatform plugin;
    private final Map<UUID, PendingWorldUnload> pendingUnloads = new ConcurrentHashMap<>();
    private static final Duration UNLOAD_CONFIRMATION_TTL = Duration.ofSeconds(30);
    private record PendingWorldUnload(String worldId, Instant expiresAt) { }

    public KairuClientGateway(SMPPlatform plugin) { this.plugin = plugin; }

    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2 || !args[0].matches("[a-f0-9-]{36}")) return true;
        String requestId = args[0];
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("guild-summary") || action.equals("guild-top") || action.equals("guild-invites") || action.equals("points-summary")) {
            plugin.clientView(player, action, data -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) reply(online, requestId, data.has("error") ? data.get("error").getAsString() : "Updated.", action, data);
            }));
            return true;
        }
        if (action.equals("points-history") || action.equals("points-top")) {
            String currency = require(args, 2, "Choose a currency.");
            plugin.clientPointsView(player, action, currency, data -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) reply(online, requestId, data.has("error") ? data.get("error").getAsString() : "Updated.", action, data);
            }));
            return true;
        }
        if (action.equals("guild-create")) {
            String[] values = decodeGuildCreate(require(args, 2, "Enter a guild name and tag."));
            plugin.clientGuildCreate(player, values[0], values[1], values[2], data -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) reply(online, requestId, data.has("error") ? data.get("error").getAsString() : data.get("message").getAsString(), "guild-summary", data);
            }));
            return true;
        }
        if (List.of("guild-invite", "guild-kick", "guild-promote", "guild-demote", "guild-leave", "guild-transfer-arm", "guild-transfer-confirm", "guild-accept").contains(action)) {
            UUID target = action.equals("guild-leave") ? null : requireUuid(args, 2);
            plugin.clientGuildAction(player, action, target, data -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) reply(online, requestId, data.has("error") ? data.get("error").getAsString() : data.get("message").getAsString(), "guild-summary", data);
            }));
            return true;
        }
        String message;
        try { message = perform(player, action, args); }
        catch (IllegalArgumentException exception) { message = exception.getMessage(); }
        reply(player, requestId, message == null ? "Updated." : message, null, null);
        return true;
    }

    private static String[] decodeGuildCreate(String encoded) {
        try {
            String[] values = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", 3);
            if (values.length < 2 || values[0].isBlank() || values[1].isBlank()) throw new IllegalArgumentException();
            return new String[] { values[0].trim(), values[1].trim(), values.length == 3 ? values[2].trim() : "" };
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Enter a guild name and tag in the required format."); }
    }

    private String perform(Player actor, String action, String[] args) {
        return switch (action) {
            case "status" -> "SMPPlatform connected.";
            case "setup" -> isAdmin(actor) ? "Server setup is ready. Run /kairuadmin setup preview, then /kairuadmin setup apply." : "Server setup requires administrator access.";
            case "travel" -> travel(actor, require(args, 2, "Choose a world."));
            case "heal", "feed", "teleport", "enderchest", "clear-inventory", "clear-effects", "xp-zero", "gamemode-survival", "gamemode-creative", "gamemode-adventure", "gamemode-spectator" -> playerAction(actor, action, requireUuid(args, 2));
            case "day", "night", "clear", "rain", "thunder", "pvp-on", "pvp-off" -> worldAction(actor, action, require(args, 2, "Choose a world."));
            case "world-difficulty" -> worldDifficulty(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose a difficulty."));
            case "world-rule" -> worldRule(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose a gamerule."), require(args, 4, "Choose true or false."));
            case "world-maintenance" -> worldMaintenance(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose true or false."));
            case "world-load" -> worldLoad(actor, require(args, 2, "Choose a world."));
            case "world-unload-arm" -> armWorldUnload(actor, require(args, 2, "Choose a world."));
            case "world-unload-confirm" -> confirmWorldUnload(actor, require(args, 2, "Choose a world."));
            default -> "This SMPPlatform action is not available in the client yet.";
        };
    }

    private String travel(Player player, String id) {
        WorldDefinition definition = plugin.worldRegistry().snapshot().worlds().get(id.toLowerCase(Locale.ROOT));
        if (definition == null) throw new IllegalArgumentException("That Kairu world is not registered.");
        if (!player.hasPermission(definition.accessPermission()) && !isAdmin(player)) throw new IllegalArgumentException("You do not have access to " + definition.displayName() + ".");
        if (!definition.isAvailableForPlayers()) throw new IllegalArgumentException(definition.displayName() + " is currently unavailable.");
        World world = Bukkit.getWorld(definition.minecraftWorldName());
        if (world == null) throw new IllegalArgumentException(definition.displayName() + " is not loaded.");
        player.teleport(world.getSpawnLocation());
        return "Travelling to " + definition.displayName() + ".";
    }

    private String playerAction(Player actor, String action, UUID targetId) {
        requirePermission(actor, "smpplatform.admin.players");
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) throw new IllegalArgumentException("That player is no longer online.");
        switch (action) {
            case "heal" -> target.setHealth(target.getMaxHealth());
            case "feed" -> { target.setFoodLevel(20); target.setSaturation(20); }
            case "teleport" -> actor.teleport(target.getLocation());
            case "enderchest" -> actor.openInventory(target.getEnderChest());
            case "clear-inventory" -> target.getInventory().clear();
            case "clear-effects" -> target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
            case "xp-zero" -> { target.setLevel(0); target.setExp(0); target.setTotalExperience(0); }
            case "gamemode-survival" -> target.setGameMode(GameMode.SURVIVAL);
            case "gamemode-creative" -> target.setGameMode(GameMode.CREATIVE);
            case "gamemode-adventure" -> target.setGameMode(GameMode.ADVENTURE);
            case "gamemode-spectator" -> target.setGameMode(GameMode.SPECTATOR);
            default -> throw new IllegalArgumentException("Unknown player action.");
        }
        return action.replace('-', ' ') + " applied to " + target.getName() + ".";
    }

    private String worldAction(Player actor, String action, String id) {
        requirePermission(actor, "smpplatform.admin.worlds");
        WorldDefinition definition = plugin.worldRegistry().snapshot().worlds().get(id.toLowerCase(Locale.ROOT));
        if (definition == null) throw new IllegalArgumentException("That Kairu world is not registered.");
        World world = Bukkit.getWorld(definition.minecraftWorldName());
        if (world == null) throw new IllegalArgumentException(definition.displayName() + " is not loaded.");
        switch (action) {
            case "day" -> world.setTime(1000);
            case "night" -> world.setTime(13000);
            case "clear" -> world.setStorm(false);
            case "rain" -> world.setStorm(true);
            case "thunder" -> { world.setStorm(true); world.setThundering(true); }
            case "pvp-on" -> world.setPVP(true);
            case "pvp-off" -> world.setPVP(false);
            default -> throw new IllegalArgumentException("Unknown world action.");
        }
        return definition.displayName() + " updated.";
    }

    private String worldDifficulty(Player actor, String id, String requested) {
        World world = adminWorld(actor, id);
        Difficulty difficulty = switch (requested.toLowerCase(Locale.ROOT)) {
            case "peaceful" -> Difficulty.PEACEFUL; case "easy" -> Difficulty.EASY;
            case "normal" -> Difficulty.NORMAL; case "hard" -> Difficulty.HARD;
            default -> throw new IllegalArgumentException("Unknown difficulty.");
        };
        world.setDifficulty(difficulty);
        return world.getName() + " difficulty set to " + difficulty.name().toLowerCase(Locale.ROOT) + ".";
    }

    private String worldRule(Player actor, String id, String ruleName, String value) {
        World world = adminWorld(actor, id);
        boolean enabled;
        if (value.equalsIgnoreCase("true")) enabled = true; else if (value.equalsIgnoreCase("false")) enabled = false; else throw new IllegalArgumentException("Gamerule value must be true or false.");
        GameRule<Boolean> rule = switch (ruleName.toLowerCase(Locale.ROOT)) {
            case "mob-spawning" -> GameRule.DO_MOB_SPAWNING;
            case "fire-spread" -> GameRule.DO_FIRE_TICK;
            case "weather-cycle" -> GameRule.DO_WEATHER_CYCLE;
            case "daylight-cycle" -> GameRule.DO_DAYLIGHT_CYCLE;
            case "keep-inventory" -> GameRule.KEEP_INVENTORY;
            default -> throw new IllegalArgumentException("That gamerule is not exposed by Kairu SMP.");
        };
        world.setGameRule(rule, enabled);
        return world.getName() + " updated " + ruleName + ".";
    }

    private World adminWorld(Player actor, String id) {
        requirePermission(actor, "smpplatform.admin.worlds");
        WorldDefinition definition = plugin.worldRegistry().snapshot().worlds().get(id.toLowerCase(Locale.ROOT));
        if (definition == null) throw new IllegalArgumentException("That Kairu world is not registered.");
        World world = Bukkit.getWorld(definition.minecraftWorldName());
        if (world == null) throw new IllegalArgumentException(definition.displayName() + " is not loaded.");
        return world;
    }

    private String worldMaintenance(Player actor, String id, String value) {
        requirePermission(actor, "smpplatform.admin.worlds");
        boolean enabled;
        if (value.equalsIgnoreCase("true")) enabled = true; else if (value.equalsIgnoreCase("false")) enabled = false; else throw new IllegalArgumentException("Maintenance value must be true or false.");
        WorldDefinition changed = plugin.worldRegistry().setMaintenance(id.toLowerCase(Locale.ROOT), enabled);
        if (enabled) {
            World affected = Bukkit.getWorld(changed.minecraftWorldName()); WorldDefinition hubDefinition = plugin.worldRegistry().require("spawn-hub"); World hub = Bukkit.getWorld(hubDefinition.minecraftWorldName());
            if (affected != null && hub != null) for (Player player : List.copyOf(affected.getPlayers())) player.teleport(hub.getSpawnLocation());
        }
        return changed.displayName() + (enabled ? " is in maintenance; players were sent to Spawn Hub." : " is open to players.");
    }

    private String worldLoad(Player actor, String id) {
        WorldDefinition definition = registeredAdminWorld(actor, id);
        if (!plugin.multiverse().available()) throw new IllegalArgumentException("Multiverse-Core is unavailable.");
        return plugin.multiverse().ensureLoaded(definition) ? definition.displayName() + " loaded." : "Multiverse could not load " + definition.displayName() + ".";
    }

    private String armWorldUnload(Player actor, String id) {
        WorldDefinition definition = registeredAdminWorld(actor, id);
        if (definition.type() == com.neonnexus.smpplatform.world.WorldType.HUB) throw new IllegalArgumentException("Spawn Hub cannot be unloaded.");
        if (!plugin.multiverse().available()) throw new IllegalArgumentException("Multiverse-Core is unavailable.");
        pendingUnloads.put(actor.getUniqueId(), new PendingWorldUnload(definition.id(), Instant.now().plus(UNLOAD_CONFIRMATION_TTL)));
        return "Unload armed. Confirm within 30 seconds; players will be sent to Spawn Hub.";
    }

    private String confirmWorldUnload(Player actor, String id) {
        WorldDefinition definition = registeredAdminWorld(actor, id);
        PendingWorldUnload pending = pendingUnloads.remove(actor.getUniqueId());
        if (pending == null || pending.expiresAt().isBefore(Instant.now()) || !pending.worldId().equals(definition.id())) throw new IllegalArgumentException("Unload confirmation has expired. Start again.");
        World affected = Bukkit.getWorld(definition.minecraftWorldName()); World hub = Bukkit.getWorld(plugin.worldRegistry().require("spawn-hub").minecraftWorldName());
        if (affected != null && hub != null) for (Player player : List.copyOf(affected.getPlayers())) player.teleport(hub.getSpawnLocation());
        return plugin.multiverse().unload(definition, true) ? definition.displayName() + " unloaded safely." : "Multiverse could not unload " + definition.displayName() + ".";
    }

    private WorldDefinition registeredAdminWorld(Player actor, String id) {
        requirePermission(actor, "smpplatform.admin.worlds");
        return plugin.worldRegistry().find(id.toLowerCase(Locale.ROOT)).orElseThrow(() -> new IllegalArgumentException("That Kairu world is not registered."));
    }

    private void reply(Player player, String id, String message, String view, JsonObject viewData) {
        JsonObject state = new JsonObject();
        state.addProperty("id", id); state.addProperty("authorized", true); state.addProperty("message", message);
        state.addProperty("admin", isAdmin(player)); state.addProperty("plots", player.getWorld().getName().equalsIgnoreCase("atrium")); state.addProperty("plotWorld", "The Atrium");
        state.addProperty("online", Bukkit.getOnlinePlayers().size()); state.addProperty("tps", Bukkit.getTPS()[0]);
        JsonArray permissions = new JsonArray();
        for (String action : List.of("players", "worlds", "inventory", "roles", "kick", "moderation", "hardcore", "quarry", "events")) if (permits(player, action)) permissions.add(action);
        state.add("permissions", permissions); state.add("players", players()); state.add("worlds", worlds()); state.add("travelWorlds", travelWorlds(player)); state.add("flags", readableFlags());
        if (view != null && viewData != null) state.add(switch (view) { case "guild-summary" -> "guild"; case "points-summary" -> "points"; default -> view; }, viewData);
        player.sendMessage(PREFIX + state);
    }

    private JsonArray players() { JsonArray rows = new JsonArray(); for (Player player : Bukkit.getOnlinePlayers()) { JsonObject row = new JsonObject(); row.addProperty("id", player.getUniqueId().toString()); row.addProperty("name", player.getName()); row.addProperty("world", player.getWorld().getName()); rows.add(row); } return rows; }
    private JsonArray worlds() { JsonArray rows = new JsonArray(); for (WorldDefinition world : plugin.worldRegistry().snapshot().worlds().values()) { JsonObject row = new JsonObject(); row.addProperty("id", world.id()); row.addProperty("name", world.displayName()); row.addProperty("status", world.status().name()); row.addProperty("maintenance", world.maintenanceMode()); row.addProperty("loaded", Bukkit.getWorld(world.minecraftWorldName()) != null); rows.add(row); } return rows; }
    private JsonArray travelWorlds(Player player) { JsonArray rows = new JsonArray(); for (WorldDefinition world : plugin.worldRegistry().snapshot().worlds().values()) if (world.isAvailableForPlayers() && (player.hasPermission(world.accessPermission()) || isAdmin(player))) { JsonObject row = new JsonObject(); row.addProperty("id", world.id()); row.addProperty("name", world.displayName()); rows.add(row); } return rows; }
    private static JsonArray readableFlags() { JsonArray rows = new JsonArray(); flag(rows, "Building access", "Visitors can place or break blocks", "Building", false); flag(rows, "Containers & doors", "Visitors can use chests, doors and buttons", "Interaction", false); flag(rows, "Player combat", "Players can damage one another", "Combat", false); flag(rows, "Explosions", "Explosions can affect the plot", "Safety", false); flag(rows, "Mob spawning", "Mobs can spawn in the plot", "Mobs", false); flag(rows, "Fire spread", "Fire can spread between blocks", "Safety", false); return rows; }
    private static void flag(JsonArray rows, String name, String description, String type, boolean editable) { JsonObject row = new JsonObject(); row.addProperty("name", name); row.addProperty("description", description); row.addProperty("type", type); row.addProperty("boolean", editable); rows.add(row); }
    private boolean permits(Player player, String action) { return isAdmin(player) && player.hasPermission("smpplatform.admin." + action); }
    private static boolean isAdmin(Player player) { return player.isOp() || player.hasPermission("smpplatform.admin"); }
    private static void requirePermission(Player player, String permission) { if (!player.isOp() && !player.hasPermission(permission)) throw new IllegalArgumentException("You do not have permission for that action."); }
    private static String require(String[] args, int index, String message) { if (args.length <= index || args[index].isBlank()) throw new IllegalArgumentException(message); return args[index]; }
    private static UUID requireUuid(String[] args, int index) { try { return UUID.fromString(require(args, index, "Select an online player.")); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Select an online player."); } }
}
