package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Strict normal-flow transitions. Administrative overrides live in HardcoreAdminService and require confirmation/audit. */
public final class HardcoreStateMachine {
    private static final Map<HardcoreState, EnumSet<HardcoreState>> ALLOWED = allowed();
    private HardcoreStateMachine() { }

    private static Map<HardcoreState, EnumSet<HardcoreState>> allowed() {
        Map<HardcoreState, EnumSet<HardcoreState>> transitions = new EnumMap<>(HardcoreState.class);
        transitions.put(HardcoreState.ALIVE, EnumSet.of(HardcoreState.DEAD));
        transitions.put(HardcoreState.DEAD, EnumSet.of(HardcoreState.SPECTATING));
        transitions.put(HardcoreState.SPECTATING, EnumSet.of(HardcoreState.RESET_ELIGIBLE));
        transitions.put(HardcoreState.RESET_ELIGIBLE, EnumSet.of(HardcoreState.ALIVE));
        transitions.put(HardcoreState.LOCKED, EnumSet.noneOf(HardcoreState.class));
        return transitions;
    }

    public static boolean canTransition(HardcoreState from, HardcoreState to) { return ALLOWED.get(from).contains(to); }
    public static HardcorePlayer transition(HardcorePlayer player, HardcoreState target, Instant at) {
        if (!canTransition(player.state(), target)) throw new IllegalStateException("Illegal Hardcore transition " + player.state() + " -> " + target);
        return new HardcorePlayer(player.playerId(), player.season(), target, at, player.lifeStartedAt());
    }
}
