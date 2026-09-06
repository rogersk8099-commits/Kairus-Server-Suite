package gg.neonnexus.smpplatform.integrations.skript;
import ch.njol.skript.lang.Effect; import ch.njol.skript.lang.Expression; import ch.njol.skript.lang.SkriptParser.ParseResult; import ch.njol.util.Kleenean; import org.bukkit.entity.Player; import org.bukkit.event.Event;
public final class EffNexusPoints extends Effect {
 private Expression<Number> amount; private Expression<String> currency; private Expression<Player> players; private boolean remove;
 @SuppressWarnings("unchecked") @Override public boolean init(Expression<?>[] x,int m,Kleenean d,ParseResult p){remove=m==1||m==3;amount=(Expression<Number>)x[0];if(m>=2){currency=(Expression<String>)x[1];players=(Expression<Player>)x[2];}else players=(Expression<Player>)x[1];return true;}
 @Override protected void execute(Event e){Number n=amount.getSingle(e);if(n==null)return;long a=n.longValue();if(a<0)return;String c=currency==null?"NEXUS_POINTS":currency.getSingle(e);if(c==null||c.isBlank())c="NEXUS_POINTS";for(Player p:players.getArray(e)){if(remove)NexusSkriptRegistry.bridge().removePoints(p.getUniqueId(),c,a,"SKRIPT");else NexusSkriptRegistry.bridge().addPoints(p.getUniqueId(),c,a,"SKRIPT");}}
 @Override public String toString(Event e,boolean d){return "nexus points effect";}
}
