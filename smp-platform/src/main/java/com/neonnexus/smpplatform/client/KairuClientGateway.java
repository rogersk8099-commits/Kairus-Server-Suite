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
        if (action.equals("build-submit")) {
            String[] values = decodeBuildSubmit(require(args, 2, "Enter a build title."));
            String message;
            try { message = plugin.submitAtriumBuild(player, values[0], values[1]); }
            catch (IllegalArgumentException exception) { message = exception.getMessage(); }
            reply(player, requestId, message, null, null);
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

    /** Only simple text is passed back into the Bukkit command parser; /build repeats all authority checks. */
    private static String[] decodeBuildSubmit(String encoded) {
        try {
            String[] values = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", 2);
            String title = values[0].trim(); String description = values.length == 2 ? values[1].trim() : "";
            if (!title.matches("[A-Za-z0-9 .,!?()'_-]{1,80}") || (!description.isBlank() && !description.matches("[A-Za-z0-9 .,!?()'_-]{1,2000}"))) throw new IllegalArgumentException();
            return new String[] { title, description };
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Use a plain build title and optional plain description (letters, numbers and basic punctuation). "); }
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
            case "plot-info", "plot-home", "plot-claim", "plot-auto" -> plotCommand(actor, action.substring("plot-".length()), null);
            case "plot-add", "plot-trust", "plot-remove", "plot-deny", "plot-undeny" -> plotCommand(actor, action.substring("plot-".length()), requireUuid(args, 2));
            case "plot-flag" -> plotBooleanFlag(actor, require(args, 2, "Choose a PlotSquared setting."), require(args, 3, "Choose a value."));
            case "plot-flag-value" -> plotTypedFlag(actor, decodeValue(require(args, 2, "Choose a PlotSquared setting.")), decodeValue(require(args, 3, "Enter a value.")));
            case "role-add", "role-remove" -> updateLuckPermsRole(actor, action, requireUuid(args, 2), require(args, 3, "Choose a role."));
            case "build-review-queue" -> { plugin.openAtriumReviewQueue(actor); yield "Opening the Atrium review queue."; }
            case "build-showcase" -> { plugin.openAtriumShowcase(actor); yield "Opening featured Atrium builds."; }
            default -> "This SMPPlatform action is not available in the client yet.";
        };
    }

    /** Delegates the final permission/plot-ownership decision to PlotSquared as the player. */
    private String plotBooleanFlag(Player player, String flagId, String value) {
        if (!List.of("pvp", "explosion", "fly", "redstone", "untrusted-visit", "block-burn", "block-ignition").contains(flagId) || !(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
            throw new IllegalArgumentException("That PlotSquared switch is not supported by this menu.");
        }
        return applyPlotFlag(player, flagId, value.toLowerCase(Locale.ROOT));
    }

    private String plotTypedFlag(Player player, String flagId, String value) {
        String normalized = value.trim();
        switch (flagId) {
            case "time" -> {
                if (!normalized.matches("[0-9]{1,5}") || Integer.parseInt(normalized) > 24000) throw new IllegalArgumentException("Plot time must be a number from 0 to 24000.");
            }
            case "gamemode" -> {
                normalized = normalized.toLowerCase(Locale.ROOT);
                if (!List.of("survival", "creative", "adventure", "spectator").contains(normalized)) throw new IllegalArgumentException("Plot gamemode must be survival, creative, adventure, or spectator.");
            }
            case "greeting", "farewell" -> {
                if (normalized.isBlank() || normalized.length() > 120 || !normalized.matches("[A-Za-z0-9 .,!?()'_-]+")) throw new IllegalArgumentException("Messages may be 1-120 plain characters.");
            }
            case "break", "place", "use" -> {
                if (!normalized.matches("(?:minecraft:)?[a-z0-9_]+(?:,(?:minecraft:)?[a-z0-9_]+){0,31}")) throw new IllegalArgumentException("Use a comma-separated material list, for example minecraft:chest,minecraft:oak_door.");
            }
            default -> throw new IllegalArgumentException("That typed PlotSquared setting is not supported by this menu.");
        }
        return applyPlotFlag(player, flagId, normalized);
    }

    private String applyPlotFlag(Player player, String flagId, String value) {
        boolean inAtrium = plugin.worldRegistry().find("atrium").map(definition -> definition.minecraftWorldName().equalsIgnoreCase(player.getWorld().getName())).orElse(false);
        if (!inAtrium) throw new IllegalArgumentException("Plot settings are available only in The Atrium.");
        if (Bukkit.getPluginManager().getPlugin("PlotSquared") == null) throw new IllegalArgumentException("PlotSquared is not available on this server.");
        if (!flagId.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("That PlotSquared setting is not valid.");
        if (!value.matches("[A-Za-z0-9_.,:+-]{1,96}")) throw new IllegalArgumentException("That setting value contains unsupported characters.");
        if (!player.performCommand("plot flag set " + flagId + " " + value)) throw new IllegalArgumentException("PlotSquared did not accept that setting. Check your plot ownership and permissions.");
        return "PlotSquared received the " + flagId + " setting.";
    }

    /** PlotSquared remains the authority for ownership and its own detailed permission checks. */
    private String plotCommand(Player actor, String command, UUID targetId) {
        boolean inAtrium = plugin.worldRegistry().find("atrium").map(definition -> definition.minecraftWorldName().equalsIgnoreCase(actor.getWorld().getName())).orElse(false);
        if (!inAtrium) throw new IllegalArgumentException("Plot controls are available only in The Atrium.");
        if (Bukkit.getPluginManager().getPlugin("PlotSquared") == null) throw new IllegalArgumentException("PlotSquared is not available on this server.");
        if (!List.of("info", "home", "claim", "auto", "add", "trust", "remove", "deny", "undeny").contains(command)) throw new IllegalArgumentException("That plot action is not supported.");
        String suffix = "";
        if (targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) throw new IllegalArgumentException("That player is no longer online.");
            suffix = " " + target.getName();
        }
        if (!actor.performCommand("plot " + command + suffix)) throw new IllegalArgumentException("PlotSquared did not accept that action. Check your plot ownership and permissions.");
        return "PlotSquared received the " + command + " request.";
    }

    /** Console dispatch is deliberately limited to the platform's assignable LuckPerms groups. */
    private String updateLuckPermsRole(Player actor, String action, UUID targetId, String requestedRole) {
        requirePermission(actor, "smpplatform.admin.roles");
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) throw new IllegalArgumentException("LuckPerms is not available on this server.");
        String role = requestedRole.toLowerCase(Locale.ROOT);
        if (!List.of("member", "builder", "helper", "moderator", "administrator").contains(role)) throw new IllegalArgumentException("That LuckPerms role is not assignable from this menu.");
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) throw new IllegalArgumentException("That player is no longer online.");
        String verb = action.equals("role-add") ? "add" : "remove";
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + target.getName() + " parent " + verb + " " + role)) throw new IllegalArgumentException("LuckPerms did not accept that role update.");
        return (verb.equals("add") ? "Granted " : "Removed ") + role + " for " + target.getName() + ".";
    }

    private static String decodeValue(String encoded) {
        try { return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("The setting value could not be read."); }
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
        boolean inAtrium = plugin.worldRegistry().find("atrium").map(definition -> definition.minecraftWorldName().equalsIgnoreCase(player.getWorld().getName())).orElse(false);
        state.addProperty("admin", isAdmin(player)); state.addProperty("plots", inAtrium); state.addProperty("plotWorld", "The Atrium");
        state.addProperty("online", Bukkit.getOnlinePlayers().size()); state.addProperty("tps", Bukkit.getTPS()[0]);
        JsonObject profile = new JsonObject();
        profile.addProperty("name", player.getName()); profile.addProperty("world", player.getWorld().getName());
        profile.addProperty("gamemode", player.getGameMode().name().toLowerCase(Locale.ROOT));
        profile.addProperty("health", Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth()));
        profile.addProperty("food", player.getFoodLevel() + "/20"); state.add("profile", profile);
        JsonArray permissions = new JsonArray();
        for (String action : List.of("players", "worlds", "inventory", "roles", "kick", "moderation", "hardcore", "quarry", "events", "creative")) if (permits(player, action)) permissions.add(action);
        state.add("permissions", permissions); state.add("players", players()); state.add("worlds", worlds()); state.add("travelWorlds", travelWorlds(player)); state.add("flags", readableFlags());
        if (permits(player, "roles")) state.add("roles", manageableRoles());
        if (view != null && viewData != null) {
            String key = switch (view) { case "guild-summary" -> "guild"; case "points-summary" -> "points"; default -> view; };
            state.add(key, viewData);
            // Older Compose builds expected balances at the top level. Keep that shape too,
            // so an upgrade never leaves the Points page blank after a successful response.
            if (view.equals("points-summary") && viewData.has("balances")) state.add("balances", viewData.get("balances"));
        }
        player.sendMessage(PREFIX + state);
    }

    private JsonArray players() { JsonArray rows = new JsonArray(); for (Player player : Bukkit.getOnlinePlayers()) { JsonObject row = new JsonObject(); row.addProperty("id", player.getUniqueId().toString()); row.addProperty("name", player.getName()); row.addProperty("world", player.getWorld().getName()); rows.add(row); } return rows; }
    @SuppressWarnings("removal")
    private JsonArray worlds() { JsonArray rows = new JsonArray(); for (WorldDefinition world : plugin.worldRegistry().snapshot().worlds().values()) { JsonObject row = new JsonObject(); World loaded = Bukkit.getWorld(world.minecraftWorldName()); row.addProperty("id", world.id()); row.addProperty("name", world.displayName()); row.addProperty("status", world.status().name()); row.addProperty("maintenance", world.maintenanceMode()); row.addProperty("loaded", loaded != null); if (loaded != null) { row.addProperty("pvp", loaded.getPVP()); row.addProperty("difficulty", loaded.getDifficulty().name().toLowerCase(Locale.ROOT)); row.addProperty("mobSpawning", Boolean.TRUE.equals(loaded.getGameRuleValue(GameRule.DO_MOB_SPAWNING))); row.addProperty("fireSpread", Boolean.TRUE.equals(loaded.getGameRuleValue(GameRule.DO_FIRE_TICK))); row.addProperty("keepInventory", Boolean.TRUE.equals(loaded.getGameRuleValue(GameRule.KEEP_INVENTORY))); } rows.add(row); } return rows; }
    private JsonArray travelWorlds(Player player) { JsonArray rows = new JsonArray(); for (WorldDefinition world : plugin.worldRegistry().snapshot().worlds().values()) if (world.isAvailableForPlayers() && (player.hasPermission(world.accessPermission()) || isAdmin(player))) { JsonObject row = new JsonObject(); row.addProperty("id", world.id()); row.addProperty("name", world.displayName()); rows.add(row); } return rows; }
    private static JsonArray readableFlags() {
        JsonArray rows = new JsonArray();
        flag(rows, "break", "Guest block breaking", "A material list that permits guests to break selected blocks.", "Materials", false);
        flag(rows, "place", "Guest block placing", "A material list that permits guests to place selected blocks.", "Materials", false);
        flag(rows, "use", "Containers & doors", "A material list that permits guests to use selected blocks.", "Materials", false);
        flag(rows, "pvp", "Player combat", "Allow or block players damaging one another in this plot.", "Combat", true);
        flag(rows, "explosion", "Explosions", "Allow or block explosions inside this plot.", "Safety", true);
        flag(rows, "fly", "Flight", "Allow or block flight while a player is inside this plot.", "Movement", true);
        flag(rows, "redstone", "Redstone", "Allow or block redstone behaviour inside this plot.", "Interaction", true);
        flag(rows, "untrusted-visit", "Guest visits", "Allow or block visitors who are not plot members.", "Access", true);
        flag(rows, "block-burn", "Block burning", "Allow or block blocks burning in this plot.", "Safety", true);
        flag(rows, "block-ignition", "Block ignition", "Allow or block blocks being set on fire in this plot.", "Safety", true);
        flag(rows, "greeting", "Welcome message", "Text shown when a player enters the plot.", "Text", false);
        flag(rows, "farewell", "Goodbye message", "Text shown when a player leaves the plot.", "Text", false);
        flag(rows, "time", "Local plot time", "A numeric simulated time for the plot.", "Number", false);
        flag(rows, "gamemode", "Plot gamemode", "The gamemode applied while inside the plot.", "Choice", false);
        return rows;
    }
    private static void flag(JsonArray rows, String id, String name, String description, String type, boolean editable) { JsonObject row = new JsonObject(); row.addProperty("id", id); row.addProperty("name", name); row.addProperty("description", description); row.addProperty("type", type); row.addProperty("boolean", editable); rows.add(row); }
    private static JsonArray manageableRoles() { JsonArray rows = new JsonArray(); for (String name : List.of("member", "builder", "helper", "moderator", "administrator")) { JsonObject role = new JsonObject(); role.addProperty("name", name); rows.add(role); } return rows; }
    private boolean permits(Player player, String action) { return player.isOp() || (isAdmin(player) && player.hasPermission("smpplatform.admin." + action)); }
    private static boolean isAdmin(Player player) { return player.isOp() || player.hasPermission("smpplatform.admin"); }
    private static void requirePermission(Player player, String permission) { if (!player.isOp() && !player.hasPermission(permission)) throw new IllegalArgumentException("You do not have permission for that action."); }
    private static String require(String[] args, int index, String message) { if (args.length <= index || args[index].isBlank()) throw new IllegalArgumentException(message); return args[index]; }
    private static UUID requireUuid(String[] args, int index) { try { return UUID.fromString(require(args, index, "Select an online player.")); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Select an online player."); } }
}
