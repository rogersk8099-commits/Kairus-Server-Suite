package gg.neonnexus.smpplatform.phase3.gui;

import gg.neonnexus.smpplatform.phase3.guild.Guild;
import gg.neonnexus.smpplatform.phase3.guild.GuildMember;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.LeaderboardEntry;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.PointTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renderer-neutral inventory/form models. Parent adapters may render these as Paper inventories or
 * Geyser forms without changing action identifiers or permission checks.
 */
public final class Phase3GuiModels {
    private Phase3GuiModels() { }
    public record GuiItem(String id, String materialKey, String miniMessageName, List<String> miniMessageLore,
                          String action, boolean enabled) {
        public GuiItem { Objects.requireNonNull(id); Objects.requireNonNull(materialKey); Objects.requireNonNull(miniMessageName); miniMessageLore=List.copyOf(miniMessageLore); Objects.requireNonNull(action); }
    }
    public record GuiScreen(String id, String miniMessageTitle, int rows, List<GuiItem> items) {
        public GuiScreen { if(rows<1||rows>6)throw new IllegalArgumentException("rows must be 1-6");items=List.copyOf(items); }
    }
    public static GuiScreen guildOverview(Guild guild, boolean mayManage) {
        List<GuiItem> items=new ArrayList<>();
        items.add(new GuiItem("guild-info","PLAYER_HEAD","<gradient:#d946ef:#60a5fa>"+guild.name()+"</gradient>",List.of("<gray>["+guild.tag()+"]</gray>","<gray>Members: "+guild.members().size()+"</gray>","<gold>Guild Points: "+guild.points()+"</gold>"),"guild.info",true));
        items.add(new GuiItem("members","PLAYER_HEAD","<aqua>Members</aqua>",List.of("<gray>Browse ranks and members</gray>"),"guild.members",true));
        items.add(new GuiItem("top","GOLD_INGOT","<gold>Guild Leaderboard</gold>",List.of("<gray>Top guilds by Guild Points</gray>"),"guild.top",true));
        if(mayManage){items.add(new GuiItem("invite","LIME_DYE","<green>Invite Player</green>",List.of("<gray>Use a compatible text input flow</gray>"),"guild.invite",true));items.add(new GuiItem("manage","NETHER_STAR","<light_purple>Manage Guild</light_purple>",List.of("<gray>Ranks, tag, description</gray>"),"guild.manage",true));}
        return new GuiScreen("guild.overview","<dark_gray>◆ </dark_gray><light_purple>Neon Nexus Guild</light_purple>",3,items);
    }
    public static GuiScreen guildMembers(Guild guild, boolean mayManage) {
        List<GuiItem> items=new ArrayList<>();
        for(GuildMember member:guild.members())items.add(new GuiItem("member:"+member.playerId(),"PLAYER_HEAD","<white>"+member.playerId()+"</white>",List.of("<gray>Rank: "+member.rank()+"</gray>","<gray>Joined: "+member.joinedAt()+"</gray>"),mayManage?"guild.member.manage:"+member.playerId():"none",mayManage));
        items.add(back()); return new GuiScreen("guild.members","<light_purple>Guild Members</light_purple>",6,items);
    }
    public static GuiScreen pointsBalance(String currency, long balance, List<PointTransaction> recent) {
        List<GuiItem> items=new ArrayList<>();items.add(new GuiItem("balance","AMETHYST_SHARD","<light_purple>"+currency+"</light_purple>",List.of("<gold>Balance: "+balance+"</gold>"),"points.history",true));
        for(PointTransaction transaction:recent)items.add(new GuiItem("tx:"+transaction.id(),transaction.amount()>0?"LIME_DYE":"RED_DYE",(transaction.amount()>0?"<green>+":"<red>")+transaction.amount()+" "+currency,List.of("<gray>"+transaction.reason()+"</gray>","<dark_gray>"+transaction.source()+" · "+transaction.occurredAt()+"</dark_gray>"),"none",false));
        return new GuiScreen("points.balance","<dark_gray>◆ </dark_gray><aqua>Points</aqua>",4,items);
    }
    public static GuiScreen pointsTop(String currency,List<LeaderboardEntry> entries) {
        List<GuiItem> items=new ArrayList<>();for(LeaderboardEntry entry:entries)items.add(new GuiItem("rank:"+entry.rank(),"GOLD_INGOT","<gold>#"+entry.rank()+"</gold> <white>"+entry.account().ownerId()+"</white>",List.of("<aqua>"+currency+": "+entry.balance()+"</aqua>"),"points.inspect:"+entry.account().ownerId(),true));items.add(back());return new GuiScreen("points.top","<aqua>"+currency+" Top</aqua>",6,items);
    }
    private static GuiItem back(){return new GuiItem("back","ARROW","<gray>Back</gray>",List.of("<dark_gray>Return</dark_gray>"),"back",true);}
}
