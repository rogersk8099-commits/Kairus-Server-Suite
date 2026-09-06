package gg.neonnexus.smpplatform.phase3.command;

import gg.neonnexus.smpplatform.phase3.common.Actor;
import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.guild.*;
import java.util.List;
import java.util.UUID;

/** Async command dispatcher for /guild and /g. The parent plugin runs execute off-thread. */
public final class GuildCommandHandler {
    private final GuildService guilds; private final PlayerDirectory players;
    public GuildCommandHandler(GuildService guilds, PlayerDirectory players) { this.guilds=guilds; this.players=players; }
    public CommandReply execute(Actor actor, String[] args) {
        try {
            if (args.length==0) return CommandReply.gui("guild.overview", "Opening Guilds");
            return switch(args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "create" -> create(actor,args); case "invite" -> invite(actor,args); case "join","accept" -> accept(actor,args);
                case "decline" -> decline(actor,args); case "leave" -> leave(actor); case "kick" -> kick(actor,args);
                case "promote" -> rank(actor,args,true); case "demote" -> rank(actor,args,false); case "transfer" -> transfer(actor,args);
                case "edit" -> edit(actor,args); case "disband" -> disband(actor); case "info" -> info(args); case "members" -> members(args); case "top" -> top(args);
                default -> help();
            };
        } catch (Phase3Exception | IllegalArgumentException exception) { return CommandReply.error(exception.getMessage()); }
    }
    private CommandReply create(Actor a,String[] x){ if(x.length<3)return CommandReply.error("Usage: /guild create <name> <tag> [description]");Guild g=guilds.create(a,x[1],x[2],join(x,3));return CommandReply.gui("guild.manage","Created " + g.name()+" ["+g.tag()+"]"); }
    private CommandReply invite(Actor a,String[] x){UUID id=target(x,1,"Usage: /guild invite <player>");GuildInvite i=guilds.invite(a,id);return CommandReply.ok("Invite sent; expires " + i.expiresAt());}
    private CommandReply accept(Actor a,String[] x){UUID id=uuid(x,1,"Usage: /guild join <guild-uuid>");Guild g=guilds.accept(a,id);return CommandReply.gui("guild.overview","Joined "+g.name());}
    private CommandReply decline(Actor a,String[] x){guilds.decline(a,uuid(x,1,"Usage: /guild decline <guild-uuid>"));return CommandReply.ok("Guild invite declined");}
    private CommandReply leave(Actor a){Guild g=guilds.leave(a);return CommandReply.ok("Left "+g.name());}
    private CommandReply kick(Actor a,String[] x){Guild g=guilds.kick(a,target(x,1,"Usage: /guild kick <player>"));return CommandReply.ok("Member removed from "+g.name());}
    private CommandReply rank(Actor a,String[] x,boolean up){Guild g=up?guilds.promote(a,target(x,1,"Usage: /guild promote <player>")):guilds.demote(a,target(x,1,"Usage: /guild demote <player>"));return CommandReply.ok(up?"Member promoted in "+g.name():"Member demoted in "+g.name());}
    private CommandReply transfer(Actor a,String[] x){Guild g=guilds.transferOwnership(a,target(x,1,"Usage: /guild transfer <player>"));return CommandReply.ok("Ownership transferred to "+g.member(g.ownerId()).playerId());}
    private CommandReply edit(Actor a,String[] x){if(x.length<3)return CommandReply.error("Usage: /guild edit <tag> <description>");Guild g=guilds.edit(a,join(x,2),x[1]);return CommandReply.ok("Updated "+g.name());}
    private CommandReply disband(Actor a){guilds.disband(a);return CommandReply.ok("Guild disbanded");}
    private CommandReply info(String[] x){Guild g=guilds.info(x.length>1?x[1]:throwUsage("Usage: /guild info <name|tag>"));return CommandReply.gui("guild.info",""+g.name()+" ["+g.tag()+"] — "+g.members().size()+" members");}
    private CommandReply members(String[] x){Guild g=guilds.info(x.length>1?x[1]:throwUsage("Usage: /guild members <name|tag>"));List<String> lines=g.members().stream().map(m->m.rank()+": "+m.playerId()).toList();return new CommandReply(true,lines,"guild.members");}
    private CommandReply top(String[] x){int n=x.length>1?Integer.parseInt(x[1]):10;return new CommandReply(true,guilds.top(n).stream().map(g->g.name()+" — "+g.points()+" GP").toList(),"guild.top");}
    private UUID target(String[] x,int index,String usage){if(x.length<=index)throwUsage(usage);return players.findUuid(x[index]).orElseThrow(()->new Phase3Exception(Phase3Exception.Code.NOT_FOUND,"Player not found"));}
    private static UUID uuid(String[] x,int index,String usage){if(x.length<=index)throwUsage(usage);try{return UUID.fromString(x[index]);}catch(IllegalArgumentException e){throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT,"Expected guild UUID");}}
    private static String join(String[] x,int start){return start>=x.length?"":String.join(" ",java.util.Arrays.copyOfRange(x,start,x.length));}
    private static String throwUsage(String usage){throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT,usage);}
    private static CommandReply help(){return CommandReply.gui("guild.overview","/guild create, invite, join, leave, info, members, top");}
}
