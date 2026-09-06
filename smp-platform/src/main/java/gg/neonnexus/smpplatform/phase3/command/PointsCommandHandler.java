package gg.neonnexus.smpplatform.phase3.command;

import gg.neonnexus.smpplatform.phase3.common.Actor;
import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.points.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.neonnexus.smpplatform.phase3.points.PointsDomain.*;

/** Async dispatcher for /points, including add/remove/set/inspect. */
public final class PointsCommandHandler {
    private final PointsService points; private final PlayerDirectory players;
    public PointsCommandHandler(PointsService points,PlayerDirectory players){this.points=points;this.players=players;}
    public CommandReply execute(Actor actor,String[] args){try{if(args.length==0)return balance(actor,actor.playerId(),"NEXUS_POINTS");return switch(args[0].toLowerCase(java.util.Locale.ROOT)){case "balance"->balance(actor,actor.playerId(),currency(args,1));case "history"->history(actor,actor.playerId(),currency(args,1));case "top"->top(args);case "add"->add(actor,args);case "remove"->remove(actor,args);case "set"->set(actor,args);case "inspect"->inspect(actor,args);default->CommandReply.error("Usage: /points [balance|history|top|add|remove|set|inspect]");};}catch(Phase3Exception|IllegalArgumentException e){return CommandReply.error(e.getMessage());}}
    private CommandReply balance(Actor a,UUID id,String cur){requireView(a);return CommandReply.gui("points.balance",cur+": "+points.balance(id,cur));}
    private CommandReply history(Actor a,UUID id,String cur){requireView(a);return new CommandReply(true,points.history(a,id,cur,20,null).stream().map(t->t.amount()+" "+t.currencyId()+" — "+t.reason()).toList(),"points.history");}
    private CommandReply top(String[] a){String cur=currency(a,1);int n=a.length>2?Integer.parseInt(a[2]):10;return new CommandReply(true,points.top(cur,OwnerType.PLAYER,n).stream().map(e->"#"+e.rank()+" "+e.account().ownerId()+" — "+e.balance()).toList(),"points.top");}
    private CommandReply add(Actor a,String[] x){UUID p=target(x,1);String c=currency(x,2);long n=amount(x,3);return CommandReply.ok("Added "+points.add(a,p,c,n,reason(x,4),Map.of("command","points add")).amount()+" "+c);}
    private CommandReply remove(Actor a,String[] x){UUID p=target(x,1);String c=currency(x,2);long n=amount(x,3);return CommandReply.ok("Removed "+-points.remove(a,p,c,n,reason(x,4),Map.of("command","points remove")).amount()+" "+c);}
    private CommandReply set(Actor a,String[] x){UUID p=target(x,1);String c=currency(x,2);long n=amount(x,3);return CommandReply.ok("Set balance to "+points.set(a,p,c,n,reason(x,4),Map.of("command","points set")).balanceAfter()+" "+c);}
    private CommandReply inspect(Actor a,String[] x){UUID p=target(x,1);String c=currency(x,2);PointAccount account=points.inspect(a,p,c);return CommandReply.gui("points.admin.inspect",c+": "+account.balance()+" (v"+account.version()+")");}
    private UUID target(String[] x,int i){if(x.length<=i)throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT,"Missing player");return players.findUuid(x[i]).orElseThrow(()->new Phase3Exception(Phase3Exception.Code.NOT_FOUND,"Player not found"));}
    private static String currency(String[] x,int i){return i<x.length?requireCurrency(x[i]):"NEXUS_POINTS";} private static long amount(String[] x,int i){if(i>=x.length)throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT,"Missing amount");long n=Long.parseLong(x[i]);if(n<0)throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT,"Amount must not be negative");return n;} private static String reason(String[] x,int i){return i<x.length?String.join(" ",java.util.Arrays.copyOfRange(x,i,x.length)):"Administrative adjustment";} private static void requireView(Actor a){if(!a.has("smpplatform.points.view")&&!a.isPointsAdmin())throw new Phase3Exception(Phase3Exception.Code.FORBIDDEN,"Missing smpplatform.points.view");}
}
