package uk.kairu.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/**
 * Kairu's server-authoritative action surface.
 *
 * The distributable client embeds the pinned OneConfig Fabric bootstrap (see
 * Build-OneConfig-Client.ps1). This action surface deliberately remains separate
 * from persistent client settings: guild, point, plot, player and world changes
 * are never stored locally and must be confirmed by SMPPlatform.
 */
public final class AdminScreen extends Screen {
    private static final int BG=0xFF12131A,SIDEBAR=0xFF0D0E14,CARD=0xFF1B1C27,HOVER=0xFF26283A,ACCENT=0xFFA97CFF,CYAN=0xFF5BDBFF,WHITE=0xFFF5F2FF,MUTED=0xFFAAA7B9,LINE=0xFF303244,GOOD=0xFF61D3A5;
    private String route="overview",target="",targetName="",query="",selectedFlag="",selectedFlagType="";
    private boolean selectedFlagBoolean;
    private int page,seenRevision,slot;
    private int administrationHeaderY=-1;
    private boolean rebuilding;
    private EditBox search;
    private Layout layout;
    private String heading="SMP menu";
    private final List<Entry> entries=new ArrayList<>();
    private record Entry(String label,Runnable action,Boolean toggle){}
    public AdminScreen(){super(Component.literal("Kairu SMP"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){build();}
    private void go(String next){route=next;page=0;query="";build();if(next.equals("guilds")||next.equals("guild-create"))KairuClient.request("guild-summary");if(next.equals("guild-top"))KairuClient.request("guild-top");if(next.equals("guild-invites"))KairuClient.request("guild-invites");if(next.equals("points"))KairuClient.request("points-summary");}
    @Override public void tick(){
        if(!KairuClient.connected()||KairuClient.state==null){onClose();return;}
        if(seenRevision!=KairuClient.revision){seenRevision=KairuClient.revision;build();}
    }
    private void build(){
        if(rebuilding||KairuClient.state==null)return;
        rebuilding=true;
        try {
            boolean focused=search!=null&&search.isFocused();
            clearWidgets();entries.clear();layout=Layout.of(width,height);
            if(!KairuClient.admin()&&!Set.of("plots","members","member","flags","flag","travel","guilds","guild-create","guild-invites","guild-top","points","points-currency","points-history","points-top","guild-invite","guild-members","guild-member","guild-leave","guild-transfer-arm","guild-transfer-confirm","world-unload-arm","world-unload-confirm").contains(route))route="travel";
            int x=layout.x(),y=layout.y(),w=layout.width();
            button(x+w-56,y+12,44,"Close",this::onClose);
            var playerTabs=new ArrayList<>(List.of("travel","guilds","points","plots"));
            var adminTabs=new ArrayList<String>(); if(KairuClient.admin())adminTabs.addAll(List.of("overview","players","worlds"));
            administrationHeaderY=-1;
            if(layout.sidebar()) {
                int navY=y+76;
                for(String tab:playerTabs){String selected=tab;navButton(x+12,navY,162,navName(tab),tab.equals(route),()->go(selected));navY+=32;}
                if(!adminTabs.isEmpty()){administrationHeaderY=navY+7;navY+=24;for(String tab:adminTabs){String selected=tab;navButton(x+12,navY,162,navName(tab),tab.equals(route),()->go(selected));navY+=32;}}
            } else {
                var tabs=new ArrayList<String>();tabs.addAll(playerTabs);tabs.addAll(adminTabs);int tabW=Math.max(28,(w-24)/tabs.size());
                for(int i=0;i<tabs.size();i++){String tab=tabs.get(i);navButton(x+12+i*tabW,y+47,tabW-4,capitalize(tab),tab.equals(route),()->go(tab));}
            }
            buildEntries();
            boolean guildCreate=route.equals("guild-create"),buildSubmit=route.equals("build-submit"),typedPlotFlag=route.equals("flag")&&!selectedFlagBoolean;
            search=new EditBox(font,layout.contentX(),layout.contentY(),Math.max(20,layout.contentWidth()),20,Component.literal(guildCreate?"Guild name | TAG | optional description":buildSubmit?"Build title | optional description":typedPlotFlag?"Enter a PlotSquared value":"Search this page"));
            search.setMaxLength(guildCreate?240:buildSubmit?2160:typedPlotFlag?120:80);search.setValue(query);search.setHint(Component.literal(guildCreate?"Example: Aurora Guild | AUR | Friends welcome":buildSubmit?"Example: Neon Library | A quiet community build":typedPlotFlag?plotValueHint(selectedFlag):"Search..."));
            search.setResponder(value->{if(!query.equals(value)){query=value;page=0;build();}});addRenderableWidget(search);
            if(focused)setInitialFocus(search);
            var visible=(guildCreate||buildSubmit||typedPlotFlag)?List.copyOf(entries):entries.stream().filter(e->e.label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
            int capacity=layout.pageSize(),maxPage=Math.max(0,(visible.size()-1)/capacity);page=Math.min(page,maxPage);
            int cellW=Math.max(20,(layout.contentWidth()-8*(layout.columns()-1))/layout.columns());
            for(int n=page*capacity;n<Math.min(visible.size(),(page+1)*capacity);n++) {
                int i=n-page*capacity;Entry entry=visible.get(n);
                settingButton(layout.contentX()+(i%layout.columns())*(cellW+8),layout.contentY()+32+(i/layout.columns())*48,cellW,entry.label,entry.toggle,entry.action);
            }
            int footer=layout.y()+layout.height()-36;
            button(x+12,footer,54,"Refresh",()->{
                if(Set.of("inventory","slot","swap","delete").contains(route)&&KairuClient.can("inventory"))KairuClient.request("inventory",target);
                else if(route.equals("guild-invites"))KairuClient.request("guild-invites");
                else if(route.startsWith("guild"))KairuClient.request("guild-summary");
                else if(route.equals("points"))KairuClient.request("points-summary");
                else if(route.equals("points-history"))KairuClient.request("points-history",target);
                else if(route.equals("points-top"))KairuClient.request("points-top",target);
                else KairuClient.request("status");
            });
            if(page>0)button(x+74,footer,42,"Prev",()->{page--;build();});
            if(page<maxPage)button(x+122,footer,42,"Next",()->{page++;build();});
        } finally {rebuilding=false;}
    }
    private void add(String label,Runnable action){entries.add(new Entry(label,action,null));}
    private void addToggle(String label,boolean enabled,Runnable action){entries.add(new Entry(label,action,enabled));}
    private void run(String... command){KairuClient.request(command);}
    private void createGuildFromForm(){
        String value=search==null?"":search.getValue().trim();String[] parts=value.split("\\|",3);
        if(parts.length<2||parts[0].trim().isEmpty()||parts[1].trim().isEmpty()){KairuClient.notice("Use: Guild name | TAG | optional description");return;}
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        run("guild-create",encoded);go("guilds");
    }
    private void submitBuildFromForm(){
        String value=search==null?"":search.getValue().trim();String[] parts=value.split("\\|",2);
        if(parts.length==0||parts[0].trim().isEmpty()){KairuClient.notice("Use: Build title | optional description");return;}
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        run("build-submit",encoded);go("plots");
    }
    private static String guildCost(JsonObject guild){return guild!=null&&guild.has("creationCost")&&guild.has("creationCurrency")?guild.get("creationCost").getAsLong()+" "+guild.get("creationCurrency").getAsString():"500 KAIRU_POINTS";}
    private static String plotValueHint(String flag){return switch(flag){case "time"->"0 to 24000";case "gamemode"->"creative, survival, adventure or spectator";case "break","place","use"->"minecraft:chest,minecraft:oak_door";default->"Plain text (up to 120 characters)";};}
    private void setTypedPlotFlag(){String value=search==null?"":search.getValue().trim();if(value.isEmpty()){KairuClient.notice("Enter a value for this PlotSquared setting.");return;}String flag=Base64.getUrlEncoder().withoutPadding().encodeToString(selectedFlag.getBytes(java.nio.charset.StandardCharsets.UTF_8));String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));run("plot-flag-value",flag,encoded);go("flags");}
    private void buildEntries(){
        var s=KairuClient.state;heading=capitalize(route);
        switch(route){
            case "overview" -> {
                add("Online players: "+s.get("online").getAsInt(),()->go("players"));
                add("TPS: "+String.format(Locale.ROOT,"%.1f",s.get("tps").getAsDouble()),()->run("status"));
                add("Player administration",()->go("players"));add("World administration",()->go("worlds"));add("LuckPerms role assignments",()->go("players"));add("Plot management",()->go("plots"));
                if(KairuClient.can("moderation"))add("Moderation: select a player",()->go("players"));
                if(KairuClient.can("hardcore"))add("Hardcore controls available in Bedrock menu",()->{});
                if(KairuClient.can("quarry"))add("Quarry controls available in Bedrock menu",()->{});
                if(KairuClient.can("events"))add("Colosseum controls available in Bedrock menu",()->{});
            }
            case "travel" -> {heading="World travel";if(!s.has("travelWorlds"))return;s.getAsJsonArray("travelWorlds").forEach(v->{JsonObject row=v.getAsJsonObject();String world=row.get("name").getAsString();add(world,()->run("travel",row.get("id").getAsString()));});}
            case "guilds" -> {
                heading="Guilds";
                if(!s.has("guild")){add("Loading guild profile...",()->run("guild-summary"));return;}
                JsonObject guild=s.getAsJsonObject("guild");
                if(guild.has("error")){add(guild.get("error").getAsString(),()->run("guild-summary"));return;}
                if(!guild.get("inGuild").getAsBoolean()){add("You are not in a guild",()->{});add("Create guild · "+guildCost(guild),()->go("guild-create"));add("Guild invitations",()->go("guild-invites"));add("Guild leaderboard",()->go("guild-top"));add("Refresh",()->run("guild-summary"));return;}
                add(guild.get("name").getAsString()+" ["+guild.get("tag").getAsString()+"]",()->{});
                add("Rank: "+guild.get("rank").getAsString()+" · Guild Points: "+guild.get("points").getAsLong()+" · Members: "+guild.getAsJsonArray("members").size(),()->{});
                add("Manage members",()->go("guild-members"));
                add("Invite an online player",()->go("guild-invite"));
                add("Guild leaderboard",()->go("guild-top"));
                add("Leave guild",()->go("guild-leave"));
                add("Disband: /guild disband",()->{});add("Refresh",()->run("guild-summary"));
            }
            case "guild-create" -> {if(!s.has("guild")||s.get("guild").isJsonNull()){heading="Create guild";add("Loading guild details...",()->run("guild-summary"));return;}heading="Create guild · "+guildCost(s.getAsJsonObject("guild"));add("Enter: Guild name | TAG | optional description",()->{});add("Create guild",this::createGuildFromForm);add("Back to guilds",()->go("guilds"));}
            case "guild-invites" -> {heading="Guild invitations";if(!s.has("guild-invites")){add("Loading invitations...",()->run("guild-invites"));return;}JsonObject data=s.getAsJsonObject("guild-invites");if(data.has("error")){add(data.get("error").getAsString(),()->run("guild-invites"));return;}if(data.getAsJsonArray("invites").isEmpty())add("You have no active guild invitations.",()->{});else data.getAsJsonArray("invites").forEach(value->{JsonObject row=value.getAsJsonObject();add("Accept "+row.get("name").getAsString()+" ["+row.get("tag").getAsString()+"]",()->{run("guild-accept",row.get("guildId").getAsString());go("guilds");});});add("Back to guilds",()->go("guilds"));}
            case "guild-invite" -> {heading="Invite to guild";s.getAsJsonArray("players").forEach(player->{JsonObject row=player.getAsJsonObject();add("Invite "+row.get("name").getAsString(),()->{run("guild-invite",row.get("id").getAsString());go("guilds");});});add("Back to guild",()->go("guilds"));}
            case "guild-members" -> {heading="Guild members";if(!s.has("guild")||s.getAsJsonObject("guild").has("error")){add("Refresh guild profile",()->run("guild-summary"));return;}JsonObject guild=s.getAsJsonObject("guild");if(!guild.get("inGuild").getAsBoolean()){go("guilds");return;}guild.getAsJsonArray("members").forEach(member->{JsonObject row=member.getAsJsonObject();String id=row.get("id").getAsString();String name=playerName(id);add(row.get("rank").getAsString()+" · "+name,()->{target=id;targetName=name;go("guild-member");});});add("Back to guild",()->go("guilds"));}
            case "guild-member" -> {heading="Guild member: "+targetName;add("Promote",()->{run("guild-promote",target);go("guilds");});add("Demote",()->{run("guild-demote",target);go("guilds");});add("Kick from guild",()->{run("guild-kick",target);go("guilds");});add("Transfer ownership...",()->go("guild-transfer-arm"));add("Back to members",()->go("guild-members"));}
            case "guild-leave" -> {heading="Leave your guild?";add("Confirm leave",()->{run("guild-leave");go("guilds");});add("Cancel",()->go("guilds"));}
            case "guild-transfer-arm" -> {heading="Transfer ownership?";add("Arm 30-second confirmation",()->{run("guild-transfer-arm",target);go("guild-transfer-confirm");});add("Cancel",()->go("guild-member"));}
            case "guild-transfer-confirm" -> {heading="Confirm: "+targetName+" becomes Leader";add("Confirm ownership transfer",()->{run("guild-transfer-confirm",target);go("guilds");});add("Cancel",()->go("guild-member"));}
            case "guild-top" -> {heading="Guild leaderboard";if(!s.has("guild-top")){add("Loading guild leaderboard...",()->run("guild-top"));return;}JsonObject data=s.getAsJsonObject("guild-top");if(data.has("error")){add(data.get("error").getAsString(),()->run("guild-top"));return;}if(data.getAsJsonArray("leaderboard").isEmpty())add("No guilds are ranked yet.",()->{});else data.getAsJsonArray("leaderboard").forEach(value->{JsonObject row=value.getAsJsonObject();add("#"+row.get("rank").getAsInt()+" · "+row.get("name").getAsString()+" ["+row.get("tag").getAsString()+"] · "+row.get("points").getAsLong()+" pts · "+row.get("members").getAsInt()+" members",()->{});});add("Back to guild",()->go("guilds"));}
            case "points" -> {
                heading="Points & currencies";
                if(!s.has("points")){add("Loading point balances...",()->run("points-summary"));return;}
                JsonObject points=s.getAsJsonObject("points");
                if(points.has("error")){add(points.get("error").getAsString(),()->run("points-summary"));return;}
                points.getAsJsonArray("balances").forEach(balance->{JsonObject row=balance.getAsJsonObject();String currency=row.get("currency").getAsString();add(currency+": "+row.get("balance").getAsLong(),()->{target=currency;go("points-currency");});});
                add("Refresh",()->run("points-summary"));
            }
            case "points-currency" -> {heading=target;add("View my transaction history",()->{run("points-history",target);go("points-history");});add("View player leaderboard",()->{run("points-top",target);go("points-top");});add("Back to balances",()->go("points"));}
            case "points-history" -> {heading=target+" / history";if(!s.has("points-history")||!s.getAsJsonObject("points-history").has("currency")||!target.equals(s.getAsJsonObject("points-history").get("currency").getAsString())){add("Loading history...",()->run("points-history",target));return;}JsonObject data=s.getAsJsonObject("points-history");if(data.has("error")){add(data.get("error").getAsString(),()->run("points-history",target));return;}if(data.getAsJsonArray("history").isEmpty())add("No transactions recorded for this currency yet.",()->{});else data.getAsJsonArray("history").forEach(value->{JsonObject row=value.getAsJsonObject();add((row.get("amount").getAsLong()>=0?"+":"")+row.get("amount").getAsLong()+" · "+row.get("reason").getAsString()+" · balance "+row.get("balance").getAsLong(),()->{});});add("Back to currency",()->go("points-currency"));}
            case "points-top" -> {heading=target+" / leaderboard";if(!s.has("points-top")||!s.getAsJsonObject("points-top").has("currency")||!target.equals(s.getAsJsonObject("points-top").get("currency").getAsString())){add("Loading leaderboard...",()->run("points-top",target));return;}JsonObject data=s.getAsJsonObject("points-top");if(data.has("error")){add(data.get("error").getAsString(),()->run("points-top",target));return;}if(data.getAsJsonArray("leaderboard").isEmpty())add("No player balances recorded for this currency yet.",()->{});else data.getAsJsonArray("leaderboard").forEach(value->{JsonObject row=value.getAsJsonObject();add("#"+row.get("rank").getAsInt()+" · "+playerName(row.get("playerId").getAsString())+" · "+row.get("balance").getAsLong(),()->{});});add("Back to currency",()->go("points-currency"));}
            case "players" -> {
                if(!KairuClient.admin())return;
                s.getAsJsonArray("players").forEach(v->{JsonObject p=v.getAsJsonObject();add(p.get("name").getAsString(),()->{
                    target=p.get("id").getAsString();targetName=p.get("name").getAsString();go("player");});});
            }
            case "player" -> {
                heading=targetName;
                if(KairuClient.can("players")){add("Heal",()->run("heal",target));add("Feed",()->run("feed",target));add("Teleport to player",()->run("teleport",target));}
                if(KairuClient.can("players")){add("Open ender chest",()->run("enderchest",target));add("Clear inventory",()->run("clear-inventory",target));add("Clear effects",()->run("clear-effects",target));add("Set XP to 0",()->run("xp-zero",target));add("Survival mode",()->run("gamemode-survival",target));add("Creative mode",()->run("gamemode-creative",target));add("Adventure mode",()->run("gamemode-adventure",target));add("Spectator mode",()->run("gamemode-spectator",target));}
                if(KairuClient.can("inventory"))add("Manage inventory",()->{KairuClient.inventory=null;run("inventory",target);go("inventory");});
                if(KairuClient.can("roles") && s.has("roles"))add("LuckPerms role assignments",()->go("roles"));
                if(KairuClient.can("kick"))add("Kick player...",()->go("kick"));add("Back to players",()->go("players"));
                if(KairuClient.can("moderation")){add("Freeze / unfreeze",()->run("freeze",target));add("Mute",()->run("mute",target));add("Ban",()->run("ban",target));}
            }
            case "roles" -> {
                heading=targetName+" / LuckPerms roles";
                if(!KairuClient.can("roles")||!s.has("roles"))return;
                s.getAsJsonArray("roles").forEach(value->{ JsonObject role=value.getAsJsonObject(); String name=role.get("name").getAsString(); add("Grant "+name,()->run("role-add",target,name));add("Remove "+name,()->run("role-remove",target,name)); });
                add("Back to player",()->go("player"));
            }
            case "kick" -> {heading="Kick "+targetName+"?";if(KairuClient.can("kick"))add("Confirm kick",()->{run("kick",target);go("players");});add("Cancel",()->go("player"));}
            case "worlds" -> {
                if(!KairuClient.can("worlds")){add("World permission required",()->go("overview"));return;}
                s.getAsJsonArray("worlds").forEach(v->{var world=v.getAsJsonObject();add(world.get("name").getAsString()+(world.get("maintenance").getAsBoolean()?" · MAINTENANCE":"")+(world.get("loaded").getAsBoolean()?"":" · UNLOADED"),()->{
                    target=world.get("id").getAsString();targetName=world.get("name").getAsString();go("world");});});
            }
            case "world" -> {
                heading=targetName;if(!KairuClient.can("worlds"))return;
                for(String a:List.of("day","night","clear","rain","thunder"))add(capitalize(a),()->run(a,target));
                add("Difficulty: Peaceful",()->run("world-difficulty",target,"peaceful"));add("Difficulty: Easy",()->run("world-difficulty",target,"easy"));add("Difficulty: Normal",()->run("world-difficulty",target,"normal"));add("Difficulty: Hard",()->run("world-difficulty",target,"hard"));
                boolean pvp=worldBoolean("pvp"),mobs=worldBoolean("mobSpawning"),fire=worldBoolean("fireSpread"),keep=worldBoolean("keepInventory");
                addToggle("Player versus player",pvp,()->run(pvp?"pvp-off":"pvp-on",target));
                addToggle("Mob spawning",mobs,()->run("world-rule",target,"mob-spawning",Boolean.toString(!mobs)));
                addToggle("Fire spread",fire,()->run("world-rule",target,"fire-spread",Boolean.toString(!fire)));
                addToggle("Keep inventory",keep,()->run("world-rule",target,"keep-inventory",Boolean.toString(!keep)));
                add("Enable maintenance (evacuate)",()->run("world-maintenance",target,"true"));add("Disable maintenance",()->run("world-maintenance",target,"false"));
                add("Load with Multiverse",()->run("world-load",target));add("Unload with Multiverse...",()->go("world-unload-arm"));
                add("Back to worlds",()->go("worlds"));
            }
            case "world-unload-arm" -> {heading="Unload "+targetName+"?";add("Arm 30-second confirmation",()->{run("world-unload-arm",target);go("world-unload-confirm");});add("Cancel",()->go("world"));}
            case "world-unload-confirm" -> {heading="Confirm unload: "+targetName;add("Confirm unload and evacuate",()->{run("world-unload-confirm",target);go("worlds");});add("Cancel",()->go("world"));}
            case "inventory" -> {
                heading=targetName+" / inventory";if(!KairuClient.can("inventory"))return;
                var inv=KairuClient.inventory;
                if(inv==null||!inv.get("target").getAsString().equals(target)){add("Fetch inventory",()->run("inventory",target));return;}
                inv.getAsJsonArray("slots").forEach(v->{var item=v.getAsJsonObject();int index=item.get("slot").getAsInt();
                    add(slotName(index)+": "+item.get("item").getAsString()+" x"+item.get("count").getAsInt(),()->{slot=index;go("slot");});});
                add("Back to player",()->go("player"));
            }
            case "slot","swap","delete" -> {
                heading=targetName+" / "+slotName(slot);if(!KairuClient.can("inventory")||KairuClient.inventory==null)return;
                if(route.equals("slot")) {
                    add("Swap with held item...",()->go("swap"));add("Delete item...",()->go("delete"));
                } else {
                    boolean swap=route.equals("swap");
                    var inv=KairuClient.inventory;var item=inv.getAsJsonArray("slots").get(slot).getAsJsonObject();
                    String itemHash=item.get("hash").getAsString(),handHash=inv.get("handHash").getAsString();
                    add(swap?"Confirm swap (empty hand = take)":"Confirm permanent deletion",()->{
                        run(swap?"inv-swap":"inv-clear",target,""+slot,itemHash,handHash);go("inventory");});
                }
                add("Cancel / back to inventory",()->go("inventory"));
            }
            case "plots" -> {
                if(!s.get("plots").getAsBoolean()){add("Enter "+s.get("plotWorld").getAsString()+" for plots",()->run("status"));return;}
                for(String a:List.of("info","home","claim","auto"))add("Plot "+a,()->run("plot-"+a));
                add("Submit current build for review",()->go("build-submit"));add("View featured Atrium builds",()->run("build-showcase"));if(KairuClient.can("creative"))add("Open staff build review queue",()->run("build-review-queue"));add("Manage members / trusted / denied",()->go("members"));add("Set plot settings",()->go("flags"));
            }
            case "build-submit" -> {heading="Submit Atrium build";add("Enter: Build title | optional description",()->{});add("Submit current claimed plot",this::submitBuildFromForm);add("Back to plot controls",()->go("plots"));}
            case "members" -> s.getAsJsonArray("players").forEach(v->{var p=v.getAsJsonObject();add(p.get("name").getAsString(),()->{
                target=p.get("id").getAsString();targetName=p.get("name").getAsString();go("member");});});
            case "member" -> {heading="Plot member: "+targetName;for(String a:List.of("add","trust","remove","deny","undeny"))add(a,()->run("plot-"+a,target));add("Back to plots",()->go("plots"));}
            case "flags" -> {s.getAsJsonArray("flags").forEach(v->{JsonObject flag=v.getAsJsonObject();String name=flag.get("name").getAsString();String type=flag.get("type").getAsString();String description=flag.get("description").getAsString();boolean editable=flag.get("boolean").getAsBoolean();add(name+" · "+description+" ["+type+"]"+(editable?"":" — view only"),()->{selectedFlag=flag.get("id").getAsString();selectedFlagType=name;selectedFlagBoolean=editable;go("flag");});});add("Back to plots",()->go("plots"));}
            case "flag" -> {heading=selectedFlagType;if(selectedFlagBoolean){add("Allow",()->run("plot-flag",selectedFlag,"true"));add("Block",()->run("plot-flag",selectedFlag,"false"));add("PlotSquared checks your ownership and permissions before changing this setting.",()->{});}else {add("Enter value above: "+plotValueHint(selectedFlag),()->{});add("Apply value",this::setTypedPlotFlag);add("PlotSquared checks your ownership and permissions before changing this setting.",()->{});}add("Back to flags",()->go("flags"));}
            default -> route=KairuClient.admin()?"overview":"plots";
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) { /* Custom opaque surface below. */ }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(layout==null)return;
        g.fill(0,0,width,height,0xCA070811);round(g,layout.x(),layout.y(),layout.width(),layout.height(),BG);
        if(layout.sidebar())g.fill(layout.x(),layout.y(),layout.x()+184,layout.bottom(),SIDEBAR);
        g.fill(layout.x()+12,layout.y()+56,layout.x()+layout.width()-12,layout.y()+57,LINE);
        g.text(font,"KAIRU SMP",layout.x()+14,layout.y()+13,CYAN,false);
        if(layout.sidebar())g.text(font,"PLAYER SETTINGS",layout.x()+14,layout.y()+31,MUTED,false);
        if(layout.sidebar()&&administrationHeaderY>=0)g.text(font,"ADMINISTRATION",layout.x()+14,administrationHeaderY,MUTED,false);
        int titleX=layout.sidebar()?layout.contentX():layout.x()+14;
        int titleY=layout.sidebar()?layout.y()+16:layout.y()+27;
        g.text(font,clip(heading,Math.max(20,layout.width()-88)),titleX,titleY,WHITE,false);
        if(layout.sidebar())g.text(font,KairuClient.admin()?"Server-authoritative controls":"Server-authoritative player menu",titleX,layout.y()+34,MUTED,false);
        if(layout.sidebar())g.fill(layout.x()+183,layout.y()+58,layout.x()+184,layout.bottom(),LINE);
        round(g,layout.contentX(),layout.contentY(),layout.contentWidth(),22,0xFF161722);
        g.fill(layout.contentX()+8,layout.contentY()+21,layout.contentX()+layout.contentWidth()-8,layout.contentY()+22,LINE);
        super.extractRenderState(g,mx,my,delta);
        g.text(font,clip(KairuClient.message,layout.width()-24),layout.x()+12,layout.y()+layout.height()-11,MUTED,false);
    }
    private String clip(String text,int max){return font.width(text)<=max?text:font.plainSubstrByWidth(text,Math.max(1,max-12))+"...";}
    private void button(int x,int y,int w,String title,Runnable action){addRenderableWidget(new FlatButton(x,y,w,title,action));}
    private void settingButton(int x,int y,int w,String title,Boolean toggle,Runnable action){addRenderableWidget(new SettingButton(x,y,w,title,toggle,action));}
    private void navButton(int x,int y,int w,String title,boolean active,Runnable action){addRenderableWidget(new NavButton(x,y,w,title,active,action));}
    private final class FlatButton extends Button {
        FlatButton(int x,int y,int w,String title,Runnable action){super(x,y,w,24,Component.literal(title),b->{if(KairuClient.pending==null||title.equals("Close"))action.run();},DEFAULT_NARRATION);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            boolean hover=isHoveredOrFocused();round(g,getX(),getY(),getWidth(),getHeight(),hover?HOVER:CARD);
            g.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),LINE);
            if(hover)g.fill(getX()+1,getY()+5,getX()+3,getY()+getHeight()-5,ACCENT);
            g.text(font,clip(getMessage().getString(),getWidth()-14),getX()+7,getY()+8,KairuClient.pending!=null?MUTED:WHITE,false);
        }
    }
    /** A OneConfig-inspired preference row: action title, context line, and a clear affordance. */
    private final class SettingButton extends Button {
        private final Boolean toggle;
        SettingButton(int x,int y,int w,String title,Boolean toggle,Runnable action){super(x,y,w,40,Component.literal(title),b->{if(KairuClient.pending==null)action.run();},DEFAULT_NARRATION);this.toggle=toggle;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            boolean hover=isHoveredOrFocused();String raw=getMessage().getString();String[] pair=toggle==null?settingParts(raw):new String[]{raw,toggle?"Enabled":"Disabled"};
            round(g,getX(),getY(),getWidth(),getHeight(),hover?HOVER:CARD);g.fill(getX()+1,getY()+getHeight()-1,getX()+getWidth()-1,getY()+getHeight(),LINE);
            g.text(font,clip(pair[0],getWidth()-64),getX()+10,getY()+8,KairuClient.pending!=null?MUTED:WHITE,false);
            if(!pair[1].isBlank())g.text(font,clip(pair[1],getWidth()-64),getX()+10,getY()+23,MUTED,false);
            if(toggle!=null){boolean on=toggle;int color=on?GOOD:0xFF686B7B;round(g,getX()+getWidth()-36,getY()+12,26,14,color);round(g,getX()+getWidth()-(on?22:34),getY()+14,10,10,WHITE);}
            else {g.text(font,hover?"›":"›",getX()+getWidth()-16,getY()+14,hover?CYAN:MUTED,false);}
        }
    }
    private final class NavButton extends Button {
        private final boolean active;
        NavButton(int x,int y,int w,String title,boolean active,Runnable action){super(x,y,w,26,Component.literal(title),b->{if(KairuClient.pending==null)action.run();},DEFAULT_NARRATION);this.active=active;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){boolean hover=isHoveredOrFocused();int fill=active?0xFF28243B:(hover?0xFF191B27:SIDEBAR);round(g,getX(),getY(),getWidth(),getHeight(),fill);if(active)g.fill(getX(),getY()+4,getX()+3,getY()+getHeight()-4,ACCENT);else if(hover)g.fill(getX(),getY()+5,getX()+2,getY()+getHeight()-5,CYAN);g.text(font,clip(getMessage().getString(),getWidth()-18),getX()+10,getY()+9,active?WHITE:MUTED,false);}
    }
    private static void round(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){
        g.fill(x+3,y,x+w-3,y+h,color);g.fill(x,y+3,x+w,y+h-3,color);g.fill(x+1,y+1,x+w-1,y+h-1,color);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(vertical!=0){page=Math.max(0,page+(vertical<0?1:-1));build();return true;}return false;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if((search==null||!search.isFocused())&&KairuClient.openKey.matches(event)){onClose();return true;}return super.keyPressed(event);
    }
    @Override public void onClose(){KairuClient.opening=false;Minecraft.getInstance().gui.setScreen(null);}
    private static String capitalize(String s){return s.isEmpty()?s:s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);}
    private static String navName(String route){return switch(route){case "travel"->"World travel";case "guilds"->"Guilds";case "points"->"Points";case "plots"->"Atrium plots";case "overview"->"Overview";case "players"->"Players";case "worlds"->"World controls";default->capitalize(route);};}
    private static String[] settingParts(String value){int split=value.indexOf(" · ");if(split>0)return new String[]{value.substring(0,split),value.substring(split+3)};if(value.startsWith("Plot "))return new String[]{value.substring(5),"PlotSquared action"};if(value.startsWith("Grant ")||value.startsWith("Remove "))return new String[]{value,"LuckPerms role assignment"};return new String[]{value,"Click to open or apply"};}
    private boolean worldBoolean(String key){if(KairuClient.state==null||!KairuClient.state.has("worlds"))return false;for(JsonElement value:KairuClient.state.getAsJsonArray("worlds")){JsonObject world=value.getAsJsonObject();if(target.equals(world.get("id").getAsString()))return world.has(key)&&world.get(key).getAsBoolean();}return false;}
    private static String playerName(String id){if(KairuClient.state!=null&&KairuClient.state.has("players"))for(JsonElement value:KairuClient.state.getAsJsonArray("players")){JsonObject player=value.getAsJsonObject();if(player.get("id").getAsString().equals(id))return player.get("name").getAsString();}return id.length()>8?id.substring(0,8)+"…":id;}
    private static String slotName(int i){return i<9?"Hotbar "+(i+1):i<36?"Storage "+(i-8):switch(i){case 36->"Boots";case 37->"Leggings";case 38->"Chestplate";case 39->"Helmet";default->"Offhand";};}
}
