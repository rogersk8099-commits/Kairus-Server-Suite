package gg.neonnexus.smpplatform.integrations.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

abstract class AbstractPlayerExpression<T> extends SimpleExpression<T> {
    protected Expression<Player> player;
    @SuppressWarnings("unchecked")
    @Override public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delay, ParseResult parseResult) { player = (Expression<Player>) expressions[0]; return true; }
    protected Player first(Event event) { Player[] players = player.getArray(event); return players.length == 0 ? null : players[0]; }
    @Override public boolean isSingle() { return true; }
}
