package gg.neonnexus.smpplatform.integrations.skript;
import org.bukkit.entity.Player; import org.bukkit.event.Event;
public final class ExprNexusLinked extends AbstractPlayerExpression<Boolean> {
 @Override protected Boolean[] get(Event e){Player p=first(e);return p==null?new Boolean[0]:new Boolean[]{NexusSkriptRegistry.bridge().linked(p.getUniqueId())};} @Override public Class<? extends Boolean> getReturnType(){return Boolean.class;} @Override public String toString(Event e,boolean d){return "nexus linked";}
}
