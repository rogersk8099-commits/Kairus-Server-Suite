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

/** Independent native screen: no OneConfig, Compose, Kotlin, browser or shader dependencies. */
public final class AdminScreen extends Screen {
    private static final int BG=0xFF101216,SIDEBAR=0xFF0C0E12,CARD=0xFF191D25,HOVER=0xFF242A36,ACCENT=0xFFAF83FF,CYAN=0xFF53DBFA,WHITE=0xFFF3F5FA,MUTED=0xFF98A2B8,LINE=0xFF2B313D;
    private String route="overview",target="",targetName="",query="",selectedFlag="",selectedFlagType="";
    private boolean selectedFlagBoolean;
    private int page,seenRevision,slot;
    private boolean rebuilding;
    private EditBox search;
    private Layout layout;
    private String heading="SMP menu";
    private final List<Entry> entries=new ArrayList<>();
    private record Entry(String label,Runnable action){}
    public AdminScreen(){super(Component.literal("Kairu SMP"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){build();}
    private void go(String next){route=next;page=0;query="";build();if(next.equals("guilds"))KairuClient.request("guild-summary");if(next.equals("guild-top"))KairuClient.request("guild-top");if(next.equals("points"))KairuClient.request("points-summary");}
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
            if(!KairuClient.admin()&&!Set.of("plots","members","member","flags","flag","travel","guilds","guild-top","points","points-currency","points-history","points-top","guild-invite","guild-members","guild-member","guild-leave","guild-transfer-arm","guild-transfer-confirm","world-unload-arm","world-unload-confirm").contains(route))route="travel";
            int x=layout.x(),y=layout.y(),w=layout.width();
            button(x+w-56,y+12,44,"Close",this::onClose);
            var tabs=new ArrayList<String>();
            if(KairuClient.admin()){tabs.add("overview");tabs.add("players");tabs.add("worlds");}tabs.add("travel");tabs.add("guilds");tabs.add("points");tabs.add("plots");
            int tabW=layout.sidebar()?126:Math.max(28,(w-24)/tabs.size());
            for(int i=0;i<tabs.size();i++) {String tab=tabs.get(i);
                navButton(layout.sidebar()?x+12:x+12+i*tabW,layout.sidebar()?y+58+i*30:y+47,tabW-4,capitalize(tab),tab.equals(route),()->go(tab));}
            buildEntries();
            search=new EditBox(font,layout.contentX(),layout.contentY(),Math.max(20,layout.contentWidth()),20,Component.literal("Search this page"));
            search.setMaxLength(80);search.setValue(query);search.setHint(Component.literal("Search..."));
            search.setResponder(value->{if(!query.equals(value)){query=value;page=0;build();}});addRenderableWidget(search);
            if(focused)setInitialFocus(search);
            var visible=entries.stream().filter(e->e.label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
            int capacity=layout.pageSize(),maxPage=Math.max(0,(visible.size()-1)/capacity);page=Math.min(page,maxPage);
            int cellW=Math.max(20,(layout.contentWidth()-8*(layout.columns()-1))/layout.columns());
            for(int n=page*capacity;n<Math.min(visible.size(),(page+1)*capacity);n++) {
                int i=n-page*capacity;Entry entry=visible.get(n);
                button(layout.contentX()+(i%layout.columns())*(cellW+8),layout.contentY()+28+(i/layout.columns())*32,cellW,entry.label,entry.action);
            }
            int footer=layout.y()+layout.height()-36;
            button(x+12,footer,54,"Refresh",()->{
                if(Set.of("inventory","slot","swap","delete").contains(route)&&KairuClient.can("inventory"))KairuClient.request("inventory",target);
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
    private void add(String label,Runnable action){entries.add(new Entry(label,action));}
    private void run(String... command){KairuClient.request(command);}
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
                if(!guild.get("inGuild").getAsBoolean()){add("You are not in a guild",()->{});add("Create or join: use /guild create or /guild join",()->{});add("Guild leaderboard",()->go("guild-top"));add("Refresh",()->run("guild-summary"));return;}
                add(guild.get("name").getAsString()+" ["+guild.get("tag").getAsString()+"]",()->{});
                add("Rank: "+guild.get("rank").getAsString()+" · Guild Points: "+guild.get("points").getAsLong()+" · Members: "+guild.getAsJsonArray("members").size(),()->{});
                add("Manage members",()->go("guild-members"));
                add("Invite an online player",()->go("guild-invite"));
                add("Guild leaderboard",()->go("guild-top"));
                add("Leave guild",()->go("guild-leave"));
                add("Disband: /guild disband",()->{});add("Refresh",()->run("guild-summary"));
            }
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
                Set<String> assigned=new HashSet<>();
                if(s.has("assignments")) for(JsonElement value:s.getAsJsonArray("assignments")) { JsonObject row=value.getAsJsonObject(); if(row.get("id").getAsString().equals(target)) row.getAsJsonArray("roles").forEach(v->assigned.add(v.getAsString())); }
                s.getAsJsonArray("roles").forEach(value->{ JsonObject role=value.getAsJsonObject(); String name=role.get("name").getAsString(); boolean has=assigned.contains(name); add((has?"Remove ":"Grant ")+name,()->run(has?"role-remove":"role-add",target,name)); });
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
                for(String a:List.of("day","night","clear","rain","thunder","pvp-on","pvp-off"))add(capitalize(a),()->run(a,target));
                add("Difficulty: Peaceful",()->run("world-difficulty",target,"peaceful"));add("Difficulty: Easy",()->run("world-difficulty",target,"easy"));add("Difficulty: Normal",()->run("world-difficulty",target,"normal"));add("Difficulty: Hard",()->run("world-difficulty",target,"hard"));
                add("Enable mob spawning",()->run("world-rule",target,"mob-spawning","true"));add("Disable mob spawning",()->run("world-rule",target,"mob-spawning","false"));add("Enable fire spread",()->run("world-rule",target,"fire-spread","true"));add("Disable fire spread",()->run("world-rule",target,"fire-spread","false"));add("Keep inventory on",()->run("world-rule",target,"keep-inventory","true"));add("Keep inventory off",()->run("world-rule",target,"keep-inventory","false"));
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
                add("Manage members / trusted / denied",()->go("members"));add("Set boolean flags",()->go("flags"));
            }
            case "members" -> s.getAsJsonArray("players").forEach(v->{var p=v.getAsJsonObject();add(p.get("name").getAsString(),()->{
                target=p.get("id").getAsString();targetName=p.get("name").getAsString();go("member");});});
            case "member" -> {heading="Plot member: "+targetName;for(String a:List.of("add","trust","remove","deny","undeny"))add(a,()->run("plot-"+a,target));add("Back to plots",()->go("plots"));}
            case "flags" -> {s.getAsJsonArray("flags").forEach(v->{JsonObject flag=v.getAsJsonObject();String name=flag.get("name").getAsString();String type=flag.get("type").getAsString();boolean editable=flag.get("boolean").getAsBoolean();add(name+" ["+type+"]"+(editable?"":" — view only"),()->{selectedFlag=name;selectedFlagType=type;selectedFlagBoolean=editable;go("flag");});});add("Back to plots",()->go("plots"));}
            case "flag" -> {heading=selectedFlag+" / "+selectedFlagType;if(selectedFlagBoolean){add("Set true",()->run("plot-flag",selectedFlag,"true"));add("Set false",()->run("plot-flag",selectedFlag,"false"));}else add("This flag is listed from PlotSquared; value editing is not available in this menu yet.",()->{});add("Back to flags",()->go("flags"));}
            default -> route=KairuClient.admin()?"overview":"plots";
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) { /* Custom opaque surface below. */ }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(layout==null)return;
        g.fill(0,0,width,height,0xC4000000);round(g,layout.x(),layout.y(),layout.width(),layout.height(),BG);
        if(layout.sidebar())g.fill(layout.x(),layout.y(),layout.x()+154,layout.bottom(),SIDEBAR);
        g.fill(layout.x()+12,layout.y()+42,layout.x()+layout.width()-12,layout.y()+43,LINE);
        g.text(font,"KAIRU SMP",layout.x()+14,layout.y()+13,CYAN,false);
        if(layout.sidebar())g.text(font,"SERVER CONTROL PANEL",layout.x()+14,layout.y()+27,MUTED,false);
        int titleX=layout.sidebar()?layout.contentX():layout.x()+14;
        int titleY=layout.sidebar()?layout.y()+16:layout.y()+27;
        g.text(font,clip(heading,Math.max(20,layout.width()-88)),titleX,titleY,WHITE,false);
        if(layout.sidebar())g.text(font,"Settings",titleX,layout.y()+30,MUTED,false);
        if(layout.sidebar())g.fill(layout.x()+153,layout.y()+48,layout.x()+154,layout.bottom(),LINE);
        g.fill(layout.contentX(),layout.contentY()+24,layout.contentX()+layout.contentWidth(),layout.contentY()+25,LINE);
        super.extractRenderState(g,mx,my,delta);
        g.text(font,clip(KairuClient.message,layout.width()-24),layout.x()+12,layout.y()+layout.height()-11,MUTED,false);
    }
    private String clip(String text,int max){return font.width(text)<=max?text:font.plainSubstrByWidth(text,Math.max(1,max-12))+"...";}
    private void button(int x,int y,int w,String title,Runnable action){addRenderableWidget(new FlatButton(x,y,w,title,action));}
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
    private final class NavButton extends Button {
        private final boolean active;
        NavButton(int x,int y,int w,String title,boolean active,Runnable action){super(x,y,w,24,Component.literal(title),b->{if(KairuClient.pending==null)action.run();},DEFAULT_NARRATION);this.active=active;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){boolean hover=isHoveredOrFocused();int fill=active?0xFF252436:(hover?0xFF171B23:SIDEBAR);round(g,getX(),getY(),getWidth(),getHeight(),fill);if(active)g.fill(getX(),getY()+3,getX()+3,getY()+getHeight()-3,ACCENT);else if(hover)g.fill(getX(),getY()+5,getX()+2,getY()+getHeight()-5,CYAN);g.text(font,clip(getMessage().getString(),getWidth()-12),getX()+8,getY()+8,active?WHITE:MUTED,false);}
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
    private static String playerName(String id){if(KairuClient.state!=null&&KairuClient.state.has("players"))for(JsonElement value:KairuClient.state.getAsJsonArray("players")){JsonObject player=value.getAsJsonObject();if(player.get("id").getAsString().equals(id))return player.get("name").getAsString();}return id.length()>8?id.substring(0,8)+"…":id;}
    private static String slotName(int i){return i<9?"Hotbar "+(i+1):i<36?"Storage "+(i-8):switch(i){case 36->"Boots";case 37->"Leggings";case 38->"Chestplate";case 39->"Helmet";default->"Offhand";};}
}
