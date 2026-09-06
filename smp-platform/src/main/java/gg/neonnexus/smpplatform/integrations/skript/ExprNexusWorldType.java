package gg.neonnexus.smpplatform.integrations.skript;
import org.bukkit.entity.Player; import org.bukkit.event.Event;
public final class ExprNexusWorldType extends AbstractPlayerExpression<String> {
 @Override protected String[] get(Event e){ Player p=first(e); return p==null?new String[0]:NexusSkriptRegistry.bridge().worldType(p.getUniqueId()).map(v->new String[]{v}).orElseGet(()->new String[0]); }
 @Override public Class<? extends String> getReturnType(){return String.class;} @Override public String toString(Event e, boolean debug){return "nexus world type";}
}
