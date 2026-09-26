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
    private final Map<UUID, Instant> pendingQuarryResets = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> pendingClaimAbandons = new ConcurrentHashMap<>();
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
        if (action.equals("player-profile")) {
            requirePermission(player,"smpplatform.admin.players");
            if(args.length<3){reply(player,requestId,"Select a player first.",action);return true;}
            try{
                UUID targetId=UUID.fromString(args[2]);
                JsonObject data=plugin.clientPlayerProfile(targetId);
                reply(player,requestId,data.has("error")?data.get("error").getAsString():"Player profile loaded.","playerProfile",data);
            }catch(Exception e){reply(player,requestId,"Invalid player selection.",action);}
            return true;
        }
        if (action.equals("world-admin-summary")) {
            requirePermission(player,"smpplatform.admin.worlds");
            JsonObject data=plugin.clientWorldAdminSummary();
            reply(player,requestId,"World administration loaded.","worldAdmin",data);
            return true;
        }
        if (action.equals("hardcore-reset-status")) {
            requirePermission(player,"smpplatform.admin.hardcore");
            reply(player,requestId,"Reset window loaded.","hardcoreReset",plugin.clientHardcoreResetStatus());return true;
        }
        if (action.equals("hardcore-reset-configure")) {
            if(args.length<4){reply(player,requestId,"Open and close timestamps are required.",action);return true;}
            try{reply(player,requestId,plugin.clientHardcoreResetConfigure(player,args[2],args[3]),action);}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Reset window update failed.":e.getMessage(),action);}return true;
        }
        if (action.equals("hardcore-lookup")) {
            requirePermission(player,"smpplatform.admin.hardcore");
            try{UUID target=UUID.fromString(args[2]);reply(player,requestId,"Hardcore profile loaded.","hardcoreProfile",plugin.clientHardcoreLookup(target));}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Hardcore profile unavailable.":e.getMessage(),action);}return true;
        }
        if (action.equals("hardcore-state")) {
            if(args.length<4){reply(player,requestId,"Target and state are required.",action);return true;}
            try{UUID target=UUID.fromString(args[2]);String state=args[3].toUpperCase(java.util.Locale.ROOT);
                String reason=args.length>4?String.join(" ",java.util.Arrays.copyOfRange(args,4,args.length)):"Hardcore administration";
                reply(player,requestId,plugin.clientHardcoreState(player,target,state,reason),action);}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Hardcore update failed.":e.getMessage(),action);}return true;
        }
        if (action.equals("points-history")) {
            requirePermission(player,"smpplatform.admin.points.inspect");
            try{String target=args[2],currency=args.length>3?args[3]:"";int limit=args.length>4?Integer.parseInt(args[4]):25;
                reply(player,requestId,"Point history loaded.","pointHistory",plugin.clientPointHistory(target,currency,limit));}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Point history unavailable.":e.getMessage(),action);}return true;
        }
        if (action.equals("points-leaderboard")) {
            try{String currency=args.length>2?args[2]:"KAIRU_POINTS";int limit=args.length>3?Integer.parseInt(args[3]):25;
                reply(player,requestId,"Leaderboard loaded.","pointLeaderboard",plugin.clientPointLeaderboard(currency,limit));}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Leaderboard unavailable.":e.getMessage(),action);}return true;
        }
        if (action.equals("guild-search")) {
            requirePermission(player,"smpplatform.admin.guilds.info");
            try{String query=args.length>2?args[2]:"";int limit=args.length>3?Integer.parseInt(args[3]):25;
                reply(player,requestId,"Guild search loaded.","guildSearch",plugin.clientGuildSearch(query,limit));}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Guild search unavailable.":e.getMessage(),action);}return true;
        }
        if (action.equals("guild-admin") || action.equals("points-admin")) {
            String area=action.equals("guild-admin")?"guilds":"points";
            if(args.length<5){reply(player,requestId,"Operation, target and value are required.",action);return true;}
            try{
                String operation=args[2],target=args[3],value=args[4];
                String reason=args.length>5?String.join(" ",java.util.Arrays.copyOfRange(args,5,args.length)):"Client administration";
                reply(player,requestId,plugin.clientGuildPointsAdmin(player,area,operation,target,value,reason),action);
            }catch(Exception e){reply(player,requestId,e.getMessage()==null?"Administration request failed.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("world-maintenance")) {
            if(args.length<4){reply(player,requestId,"World and maintenance state are required.",action);return true;}
            try{reply(player,requestId,plugin.clientWorldMaintenance(player,args[2],Boolean.parseBoolean(args[3])),action);}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Maintenance update failed.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("world-admin")) {
            if(args.length<4){reply(player,requestId,"World and action are required.",action);return true;}
            try{
                String world=args[2],worldAction=args[3],argument=args.length>4?args[4]:"";
                reply(player,requestId,plugin.clientWorldAdminAction(player,world,worldAction,argument),action);
            }catch(Exception e){reply(player,requestId,e.getMessage()==null?"World action failed.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("inventory-slot")) {
            if(args.length<6){reply(player,requestId,"Inventory slot request is incomplete.",action);return true;}
            try{
                UUID target=UUID.fromString(args[2]);boolean ender=Boolean.parseBoolean(args[3]);int slot=Integer.parseInt(args[4]);
                String perm=ender?"smpplatform.admin.players.enderchest":"smpplatform.admin.players.inventory";requirePermission(player,perm);
                JsonObject data=plugin.clientInventorySlot(target,ender,slot);reply(player,requestId,"Slot loaded.","inventorySlot",data);
            }catch(Exception e){reply(player,requestId,e.getMessage()==null?"Slot unavailable.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("inventory-remove-prepare")) {
            if(args.length<6){reply(player,requestId,"Remove request is incomplete.",action);return true;}
            try{
                UUID target=UUID.fromString(args[2]);boolean ender=Boolean.parseBoolean(args[3]);int slot=Integer.parseInt(args[4]);String fingerprint=args[5];
                String confirmAction="inventory-remove:"+ender+":"+slot+":"+fingerprint;
                reply(player,requestId,plugin.clientAdminPrepare(player,target,confirmAction,""),"inventoryRemovePrepared");
            }catch(Exception e){reply(player,requestId,e.getMessage()==null?"Unable to prepare removal.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("inventory-edit")) {
            if(args.length<8){reply(player,requestId,"Inventory edit request is incomplete.",action);return true;}
            try{
                UUID target=UUID.fromString(args[2]);boolean ender=Boolean.parseBoolean(args[3]);String edit=args[4];
                int from=Integer.parseInt(args[5]);Integer to=args[6].equals("-")?null:Integer.valueOf(args[6]);String fingerprint=args[7];
                reply(player,requestId,plugin.clientInventoryEdit(player,target,ender,edit,from,to,fingerprint),action);
            }catch(Exception e){reply(player,requestId,e.getMessage()==null?"Inventory edit failed.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("admin-prepare")) {
            if(args.length<4){reply(player,requestId,"Target and action are required.",action);return true;}
            try{UUID target=UUID.fromString(args[2]);String pendingAction=args[3];
                reply(player,requestId,plugin.clientAdminPrepare(player,target,pendingAction,args.length>4?args[4]:""),action);
            }catch(Exception e){reply(player,requestId,"Invalid confirmation request.",action);}
            return true;
        }
        if (action.equals("admin-confirm")) {
            if(args.length<4){reply(player,requestId,"Target and action are required.",action);return true;}
            try{reply(player,requestId,plugin.clientAdminConfirm(player,UUID.fromString(args[2]),args[3]),action);}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Confirmation failed.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("moderation-history")) {
            requirePermission(player,"smpplatform.admin.players.moderation");
            if(args.length<3){reply(player,requestId,"Select a player first.",action);return true;}
            try{
                plugin.clientModerationHistory(UUID.fromString(args[2]),data->Bukkit.getScheduler().runTask(plugin,()->reply(player,requestId,
                    data.has("error")?data.get("error").getAsString():"Moderation history loaded.","moderationHistory",data)));
            }catch(Exception e){reply(player,requestId,"Invalid player selection.",action);}
            return true;
        }
        if (action.equals("moderate")) {
            if(args.length<4){reply(player,requestId,"Player and moderation action are required.",action);return true;}
            try{
                UUID target=UUID.fromString(args[2]);String modAction=args[3];
                long minutes=args.length>4?Long.parseLong(args[4]):0;
                String reason=args.length>5?String.join(" ",java.util.Arrays.copyOfRange(args,5,args.length)):"No reason supplied.";
                plugin.clientModerate(player,target,modAction,reason,minutes,message->reply(player,requestId,message,action));
            }catch(Exception e){reply(player,requestId,"Invalid moderation request.",action);}
            return true;
        }
        if (action.equals("player-inventory") || action.equals("player-ender")) {
            requirePermission(player,action.equals("player-ender")?"smpplatform.admin.players.enderchest":"smpplatform.admin.players.inventory");
            if(args.length<3){reply(player,requestId,"Select a player first.",action);return true;}
            try{
                JsonObject data=plugin.clientPlayerInventory(UUID.fromString(args[2]),action.equals("player-ender"));
                reply(player,requestId,"Inventory loaded.","playerInventory",data);
            }catch(Exception e){reply(player,requestId,e.getMessage()==null?"Inventory unavailable.":e.getMessage(),action);}
            return true;
        }
        if (action.equals("player-admin")) {
            if(args.length<4){reply(player,requestId,"Player and action are required.",action);return true;}
            try{
                UUID targetId=UUID.fromString(args[2]);
                String playerAction=args[3];
                String argument=args.length>4?String.join(" ",java.util.Arrays.copyOfRange(args,4,args.length)):"";
                reply(player,requestId,plugin.clientPlayerAdminAction(player,targetId,playerAction,argument),action);
            }catch(SecurityException e){reply(player,requestId,e.getMessage(),action);}
            catch(Exception e){reply(player,requestId,e.getMessage()==null?"Player action failed.":e.getMessage(),action);}
            return true;
        }

        if (action.equals("auction-browse") || action.equals("auction-listings") || action.equals("auction-bids")) {
            requirePermission(player, "smpplatform.auction.browse");
            java.util.function.Consumer<JsonObject> done = data -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player online=Bukkit.getPlayer(player.getUniqueId()); if(online==null)return;
                reply(online, requestId, data.has("error")?data.get("error").getAsString():"Auction listings updated.", "auctionResults", data);
            });
            if(action.equals("auction-listings")) plugin.clientAuctionMine(player,done);
            else plugin.clientAuctionBrowse(action.equals("auction-browse") && args.length>2?String.join(" ",java.util.Arrays.copyOfRange(args,2,args.length)).trim():"",done);
            return true;
        }
        if (action.equals("auction-view")) {
            requirePermission(player,"smpplatform.auction.buy");
            if(args.length<3) throw new IllegalArgumentException("Listing id is required.");
            reply(player,requestId,plugin.clientAuctionBuyPrepare(player,args[2]),action);
            return true;
        }
        if (action.equals("auction-buy")) {
            requirePermission(player,"smpplatform.auction.buy");
            plugin.clientAuctionBuyConfirm(player,message -> reply(player,requestId,message,action));
            return true;
        }
        if (action.equals("auction-collect")) {
            requirePermission(player,"smpplatform.auction.browse");
            plugin.clientAuctionCollect(player,message -> reply(player,requestId,message,action));
            return true;
        }
        if (action.equals("auction-sell")) {
            requirePermission(player,"smpplatform.auction.create");
            if(args.length>2){
                try{ double price=Double.parseDouble(args[2]); reply(player,requestId,plugin.clientAuctionSellPrepare(player,price),action); }
                catch(NumberFormatException e){reply(player,requestId,"Enter a valid sale price.",action);}
            } else plugin.clientAuctionSellConfirm(player,message->reply(player,requestId,message,action));
            return true;
        }
        if (action.equals("auction-cancel")) {
            requirePermission(player,"smpplatform.auction.cancel");
            if(args.length<3){reply(player,requestId,"Select one of your listings to cancel.",action);return true;}
            try{plugin.clientAuctionCancel(player,UUID.fromString(args[2]),message->reply(player,requestId,message,action));}
            catch(IllegalArgumentException e){reply(player,requestId,"Invalid listing id.",action);}
            return true;
        }
        if (action.equals("auction-bid")) {
            requirePermission(player,"smpplatform.auction.bid");
            if(args.length>=4){
                try{reply(player,requestId,plugin.clientAuctionBidPrepare(player,args[2],Double.parseDouble(args[3])),action);}
                catch(Exception e){reply(player,requestId,"Use a valid listing id and bid amount.",action);}
            } else plugin.clientAuctionBidConfirm(player,message->reply(player,requestId,message,action));
            return true;
        }

        if (action.equals("search-summary") || action.equals("search") || action.equals("search-filter")) {
            requirePermission(player, "smpplatform.search.use");
            String query = action.equals("search") && args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim() : "";
            String filter = action.equals("search-filter") && args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
            plugin.clientPlayerSearch(query, filter, data -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online == null) return;
                // Merge live presence without exposing private fields.
                JsonArray rows = data.has("searchResults") ? data.getAsJsonArray("searchResults") : new JsonArray();
                java.util.Set<String> known = new java.util.HashSet<>();
                rows.forEach(value -> { if (value.getAsJsonObject().has("id")) known.add(value.getAsJsonObject().get("id").getAsString()); });
                for (Player candidate : Bukkit.getOnlinePlayers()) {
                    boolean matches = query.isBlank() || candidate.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                            || candidate.getUniqueId().toString().equalsIgnoreCase(query);
                    if (!matches || filter.equals("recent") || filter.equals("offline")) continue;
                    if (known.contains(candidate.getUniqueId().toString())) {
                        rows.forEach(value -> {
                            JsonObject row = value.getAsJsonObject();
                            if (row.has("id") && row.get("id").getAsString().equals(candidate.getUniqueId().toString())) {
                                row.addProperty("online", true); row.addProperty("world", candidate.getWorld().getName());
                            }
                        });
                    } else {
                        JsonObject row = new JsonObject();
                        row.addProperty("id", candidate.getUniqueId().toString());
                        row.addProperty("name", candidate.getName());
                        row.addProperty("platform", "Minecraft");
                        row.addProperty("world", candidate.getWorld().getName());
                        row.addProperty("online", true);
                        rows.add(row);
                    }
                }
                data.add("searchResults", rows);
                reply(online, requestId, data.has("error") ? data.get("error").getAsString() : "Search updated.", "searchResults", data);
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

    private String setupStatus(Player actor) {
        requirePermission(actor, "smpplatform.admin.setup");
        return "Server setup controls are connected. Preview or validate before applying changes.";
    }

    private String runSetup(Player actor, String mode) {
        requirePermission(actor, "smpplatform.admin.setup");
        if (!Bukkit.dispatchCommand(actor, "kairuadmin setup " + mode)) throw new IllegalArgumentException("SMPPlatform setup did not accept that request.");
        return mode.equals("preview") ? "Setup preview printed to chat." : "Setup apply request completed; review the server messages.";
    }

    private String validateSetup(Player actor) {
        requirePermission(actor, "smpplatform.admin.setup");
        int missing = 0;
        for (WorldDefinition definition : plugin.worldRegistry().snapshot().worlds().values()) {
            if (Bukkit.getWorld(definition.minecraftWorldName()) == null && !definition.id().equals("spawn-hub")) missing++;
        }
        boolean lp = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        boolean mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null;
        boolean mvi = Bukkit.getPluginManager().getPlugin("Multiverse-Inventories") != null;
        boolean plots = Bukkit.getPluginManager().getPlugin("PlotSquared") != null;
        return "Validation: worlds missing=" + missing + "; LuckPerms=" + state(lp) + "; Multiverse=" + state(mv)
                + "; Inventories=" + state(mvi) + "; PlotSquared=" + state(plots) + ".";
    }

    private static String state(boolean available) { return available ? "PASS" : "WARNING"; }

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
            case "setup", "setup-status" -> setupStatus(actor);
            case "setup-preview" -> runSetup(actor, "preview");
            case "setup-apply" -> runSetup(actor, "apply");
            case "setup-validate" -> validateSetup(actor);
            case "travel" -> travel(actor, require(args, 2, "Choose a world."));
            case "heal", "feed", "teleport", "enderchest", "clear-inventory", "clear-effects", "xp-zero", "gamemode-survival", "gamemode-creative", "gamemode-adventure", "gamemode-spectator" -> playerAction(actor, action, requireUuid(args, 2));
            case "day", "night", "clear", "rain", "thunder", "pvp-on", "pvp-off" -> worldAction(actor, action, require(args, 2, "Choose a world."));
            case "world-difficulty" -> worldDifficulty(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose a difficulty."));
            case "world-rule" -> worldRule(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose a gamerule."), require(args, 4, "Choose true or false."));
            case "world-maintenance" -> worldMaintenance(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose true or false."));
            case "world-load" -> worldLoad(actor, require(args, 2, "Choose a world."));
            case "world-teleport" -> worldTeleport(actor, require(args, 2, "Choose a world."));
            case "world-set-spawn" -> worldSetSpawn(actor, require(args, 2, "Choose a world."));
            case "world-unload-arm" -> armWorldUnload(actor, require(args, 2, "Choose a world."));
            case "world-unload-confirm" -> confirmWorldUnload(actor, require(args, 2, "Choose a world."));
            case "world-protection" -> worldProtection(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose true or false."));
            case "world-placed-protection" -> worldPlacedProtection(actor, require(args, 2, "Choose a world."), require(args, 3, "Choose true or false."));
            case "quarry-reset-arm" -> armQuarryReset(actor);
            case "quarry-reset-confirm" -> confirmQuarryReset(actor);
            case "server-save" -> serverSave(actor);
            case "server-whitelist" -> serverWhitelist(actor, require(args, 2, "Choose true or false."));
            case "claim-create" -> claimCreate(actor, args.length > 2 ? require(args, 2, "Choose a claim radius.") : null);
            case "claim-info" -> plugin.worldProtection().describe(actor);
            case "claim-trust" -> claimTrust(actor, requireUuid(args, 2), true);
            case "claim-untrust" -> claimTrust(actor, requireUuid(args, 2), false);
            case "claim-abandon-arm" -> armClaimAbandon(actor);
            case "claim-abandon-confirm" -> confirmClaimAbandon(actor);
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
        if (!List.of("member", "trusted", "moderator", "admin", "owner").contains(role)) throw new IllegalArgumentException("That LuckPerms role is not assignable from this menu.");
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

    private String worldTeleport(Player actor, String id) {
        WorldDefinition definition = registeredAdminWorld(actor, id);
        if (!plugin.multiverse().available()) throw new IllegalArgumentException("Multiverse-Core is unavailable.");
        plugin.multiverse().teleport(actor, definition);
        return "Teleporting to " + definition.displayName() + ".";
    }

    private String worldSetSpawn(Player actor, String id) {
        World world = adminWorld(actor, id);
        if (!actor.getWorld().equals(world)) throw new IllegalArgumentException("Stand in the selected world before setting its spawn.");
        world.setSpawnLocation(actor.getLocation());
        return world.getName() + " spawn was set to your current location.";
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

    private String worldProtection(Player actor, String id, String value) {
        WorldDefinition definition = registeredAdminWorld(actor, id); boolean enabled = booleanValue(value);
        plugin.worldProtection().setEnabled(definition.minecraftWorldName(), enabled);
        return definition.displayName() + " build protection is " + (enabled ? "enabled" : "disabled") + ".";
    }
    private String worldPlacedProtection(Player actor, String id, String value) {
        WorldDefinition definition = registeredAdminWorld(actor, id); boolean enabled = booleanValue(value);
        plugin.worldProtection().setTrackPlaced(definition.minecraftWorldName(), enabled);
        return definition.displayName() + " placed-block protection is " + (enabled ? "enabled" : "disabled") + ".";
    }
    private String armQuarryReset(Player actor) { requirePermission(actor, "smpplatform.admin.quarry"); pendingQuarryResets.put(actor.getUniqueId(), Instant.now().plus(Duration.ofSeconds(30))); return "Quarry reset armed. Confirm within 30 seconds."; }
    private String confirmQuarryReset(Player actor) { requirePermission(actor, "smpplatform.admin.quarry"); Instant until = pendingQuarryResets.remove(actor.getUniqueId()); if (until == null || until.isBefore(Instant.now())) throw new IllegalArgumentException("Quarry reset confirmation expired. Start again."); return plugin.requestManualQuarryReset(); }
    private String serverSave(Player actor) { requirePermission(actor, "smpplatform.admin.monitor"); Bukkit.savePlayers(); Bukkit.getWorlds().forEach(World::save); return "All loaded worlds and player data were saved."; }
    private String serverWhitelist(Player actor, String value) { requirePermission(actor, "smpplatform.admin.monitor"); boolean enabled = booleanValue(value); Bukkit.setWhitelist(enabled); return "Server whitelist is " + (enabled ? "enabled" : "disabled") + "."; }
    private String claimCreate(Player actor, String requestedRadius) { int radius = requestedRadius == null ? plugin.worldProtection().defaultRadius() : parseClaimRadius(requestedRadius); var claim = plugin.worldProtection().create(actor, radius); return "Claim created: " + (claim.maxX() - claim.minX() + 1) + "×" + (claim.maxZ() - claim.minZ() + 1) + " blocks. Add builders from the Claims page."; }
    private String claimTrust(Player actor, UUID targetId, boolean add) { Player target = Bukkit.getPlayer(targetId); if (target == null) throw new IllegalArgumentException("That player is no longer online."); if (add) plugin.worldProtection().trust(actor, target); else plugin.worldProtection().untrust(actor, target); return target.getName() + (add ? " can now build in this claim." : " can no longer build in this claim."); }
    private String armClaimAbandon(Player actor) { plugin.worldProtection().ownClaimAt(actor); pendingClaimAbandons.put(actor.getUniqueId(), Instant.now().plus(Duration.ofSeconds(30))); return "Claim abandonment armed. Confirm within 30 seconds."; }
    private String confirmClaimAbandon(Player actor) { Instant until = pendingClaimAbandons.remove(actor.getUniqueId()); if (until == null || until.isBefore(Instant.now())) throw new IllegalArgumentException("Claim abandonment confirmation expired. Start again."); plugin.worldProtection().abandon(actor); return "Claim abandoned. This land is no longer protected by that claim."; }
    private static int parseClaimRadius(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Claim radius must be a whole number."); } }
    private static boolean booleanValue(String value) { if (value.equalsIgnoreCase("true")) return true; if (value.equalsIgnoreCase("false")) return false; throw new IllegalArgumentException("Choose true or false."); }

    private WorldDefinition registeredAdminWorld(Player actor, String id) {
        requirePermission(actor, "smpplatform.admin.worlds");
        return plugin.worldRegistry().find(id.toLowerCase(Locale.ROOT)).orElseThrow(() -> new IllegalArgumentException("That Kairu world is not registered."));
    }

    private void reply(Player player, String id, String message, String view) {
        reply(player, id, message, view, null);
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
        for (String action : List.of("players", "worlds", "inventory", "roles", "kick", "moderation", "hardcore", "quarry", "events", "creative", "monitor", "search", "setup", "auction")) if (permits(player, action)) permissions.add(action);
        state.add("permissions", permissions); state.add("players", players()); state.add("worlds", worlds()); state.add("travelWorlds", travelWorlds(player)); state.add("flags", readableFlags());
        if (permits(player, "roles")) state.add("roles", manageableRoles());
        if (view != null && viewData != null) {
            String key = switch (view) {
                case "guild-summary", "guild-top", "guild-invites" -> "guild";
                case "points-summary", "points-history", "points-top" -> "points";
                default -> view;
            };
            if (view.equals("hardcoreReset")) state.add("hardcoreReset",viewData);
            else if (view.equals("hardcoreProfile")) state.add("hardcoreProfile",viewData);
            else if (view.equals("pointHistory") && viewData.has("pointHistory")) state.add("pointHistory",viewData.get("pointHistory"));
            else if (view.equals("pointLeaderboard") && viewData.has("pointLeaderboard")) state.add("pointLeaderboard",viewData.get("pointLeaderboard"));
            else if (view.equals("guildSearch") && viewData.has("guildSearch")) state.add("guildSearch",viewData.get("guildSearch"));
            else if (view.equals("worldAdmin") && viewData.has("worldAdmin")) state.add("worldAdmin",viewData.get("worldAdmin"));
            else if (view.equals("inventorySlot")) state.add("inventorySlot",viewData);
            else if (view.equals("moderationHistory") && viewData.has("moderationHistory")) state.add("moderationHistory",viewData.get("moderationHistory"));
            else if (view.equals("playerInventory")) state.add("playerInventory", viewData);
            else if (view.equals("playerProfile")) state.add("playerProfile", viewData);
            else if (view.equals("auction-db-status")) state.add("auctionDatabase", viewData);
            else if (view.equals("auctionResults") && viewData.has("auctionListings")) state.add("auctionListings", viewData.get("auctionListings"));
            else if (view.equals("searchResults") && viewData.has("searchResults")) state.add("searchResults", viewData.get("searchResults"));
            else state.add(key, viewData);
            // Older Compose builds expected balances at the top level. Keep that shape too,
            // so an upgrade never leaves the Points page blank after a successful response.
            if (view.equals("points-summary") && viewData.has("balances")) state.add("balances", viewData.get("balances"));
        }
        player.sendMessage(PREFIX + state);
    }

    private JsonArray players() { JsonArray rows = new JsonArray(); for (Player player : Bukkit.getOnlinePlayers()) { JsonObject row = new JsonObject(); row.addProperty("id", player.getUniqueId().toString()); row.addProperty("name", player.getName()); row.addProperty("world", player.getWorld().getName()); rows.add(row); } return rows; }
    @SuppressWarnings("removal")
    private JsonArray worlds() { JsonArray rows = new JsonArray(); for (WorldDefinition world : plugin.worldRegistry().snapshot().worlds().values()) { JsonObject row = new JsonObject(); World loaded = Bukkit.getWorld(world.minecraftWorldName()); row.addProperty("id", world.id()); row.addProperty("name", world.displayName()); row.addProperty("status", world.status().name()); row.addProperty("maintenance", world.maintenanceMode()); row.addProperty("loaded", loaded != null); row.addProperty("protection", plugin.worldProtection() != null && plugin.worldProtection().enabled(world.minecraftWorldName())); row.addProperty("placedProtection", plugin.worldProtection() != null && plugin.worldProtection().trackPlaced(world.minecraftWorldName())); if (loaded != null) { row.addProperty("pvp", loaded.getPVP()); row.addProperty("difficulty", loaded.getDifficulty().name().toLowerCase(Locale.ROOT)); row.addProperty("mobSpawning", Boolean.TRUE.equals(loaded.getGameRuleValue(GameRule.DO_MOB_SPAWNING))); row.addProperty("fireSpread", Boolean.TRUE.equals(loaded.getGameRuleValue(GameRule.DO_FIRE_TICK))); row.addProperty("keepInventory", Boolean.TRUE.equals(loaded.getGameRuleValue(GameRule.KEEP_INVENTORY))); } rows.add(row); } return rows; }
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
    private static JsonArray manageableRoles() { JsonArray rows = new JsonArray(); for (String name : List.of("member", "trusted", "moderator", "admin", "owner")) { JsonObject role = new JsonObject(); role.addProperty("name", name); rows.add(role); } return rows; }
    private boolean permits(Player player, String action) { return player.isOp() || (isAdmin(player) && player.hasPermission("smpplatform.admin." + action)); }
    private static boolean isAdmin(Player player) { return player.isOp() || player.hasPermission("smpplatform.admin"); }
    private static void requirePermission(Player player, String permission) { if (!player.isOp() && !player.hasPermission(permission)) throw new IllegalArgumentException("You do not have permission for that action."); }
    private static String require(String[] args, int index, String message) { if (args.length <= index || args[index].isBlank()) throw new IllegalArgumentException(message); return args[index]; }
    private static UUID requireUuid(String[] args, int index) { try { return UUID.fromString(require(args, index, "Select an online player.")); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Select an online player."); } }
}
