package gg.neonnexus.smpplatform.integrations.skript;
import ch.njol.skript.lang.Expression; import ch.njol.util.Kleenean; import ch.njol.skript.lang.SkriptParser.ParseResult; import org.bukkit.entity.Player; import org.bukkit.event.Event;
public final class ExprNexusPoints extends AbstractPlayerExpression<Long> {
 private Expression<String> currency; private int pattern;
 @SuppressWarnings("unchecked") @Override public boolean init(Expression<?>[] x,int m,Kleenean d,ParseResult p){ pattern=m; if(m==1) currency=(Expression<String>)x[0]; player=(Expression<Player>)x[m==1?1:0]; return true; }
 @Override protected Long[] get(Event e){Player p=first(e);if(p==null)return new Long[0];String c=pattern==1&&currency.getSingle(e)!=null?currency.getSingle(e):"NEXUS_POINTS";return new Long[]{NexusSkriptRegistry.bridge().points(p.getUniqueId(),c)};}
 @Override public Class<? extends Long> getReturnType(){return Long.class;} @Override public String toString(Event e,boolean d){return "nexus points";}
}
