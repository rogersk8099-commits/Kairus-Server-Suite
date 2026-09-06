package gg.neonnexus.smpplatform.lifecycle.hardcore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HardcoreStateMachineTest {
    @Test void normalOneLifeFlowRequiresEachState() {
        HardcorePlayer player = new HardcorePlayer(UUID.randomUUID(), "Season 7", HardcoreState.ALIVE, Instant.EPOCH, Instant.EPOCH);
        player = HardcoreStateMachine.transition(player, HardcoreState.DEAD, Instant.EPOCH);
        player = HardcoreStateMachine.transition(player, HardcoreState.SPECTATING, Instant.EPOCH);
        player = HardcoreStateMachine.transition(player, HardcoreState.RESET_ELIGIBLE, Instant.EPOCH);
        player = HardcoreStateMachine.transition(player, HardcoreState.ALIVE, Instant.EPOCH);
        assertEquals(HardcoreState.ALIVE, player.state());
    }
    @Test void spectatorCannotBypassResetEligibility() {
        HardcorePlayer player = new HardcorePlayer(UUID.randomUUID(), "Season 7", HardcoreState.SPECTATING, Instant.EPOCH, Instant.EPOCH);
        assertThrows(IllegalStateException.class, () -> HardcoreStateMachine.transition(player, HardcoreState.ALIVE, Instant.EPOCH));
        assertFalse(HardcoreStateMachine.canTransition(HardcoreState.LOCKED, HardcoreState.ALIVE));
    }
}
