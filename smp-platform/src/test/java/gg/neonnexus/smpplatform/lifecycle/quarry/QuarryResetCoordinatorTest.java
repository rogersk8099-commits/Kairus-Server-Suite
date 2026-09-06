package gg.neonnexus.smpplatform.lifecycle.quarry;

import static org.junit.jupiter.api.Assertions.*;
import gg.neonnexus.smpplatform.lifecycle.events.DurableEvent;
import gg.neonnexus.smpplatform.lifecycle.events.DurableEventOutbox;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuarryResetCoordinatorTest {
    @Test void backupFailureAbortsBeforeUnloadOrRegeneration() {
        FakeGateway gateway = new FakeGateway(); gateway.backup = BackupResult.failed("checksum mismatch");
        QuarryResetSnapshot result = coordinator(gateway).execute("backup-fail");
        assertEquals(QuarryResetState.ABORTED, result.state());
        assertEquals(0, gateway.unloadCalls); assertEquals(0, gateway.regenerateCalls); assertTrue(gateway.unlocked);
    }
    @Test void playerRemainingAbortsBeforeAnyDestructiveOperation() {
        FakeGateway gateway = new FakeGateway(); gateway.remainingPlayers = Set.of(UUID.randomUUID());
        QuarryResetSnapshot result = coordinator(gateway).execute("players-remain");
        assertEquals(QuarryResetState.ABORTED, result.state());
        assertEquals(0, gateway.saveCalls); assertEquals(0, gateway.backupCalls); assertEquals(0, gateway.unloadCalls); assertEquals(0, gateway.regenerateCalls);
    }
    private QuarryResetCoordinator coordinator(FakeGateway gateway) { return new QuarryResetCoordinator(gateway, new InMemoryQuarryResetRepository(), new NoopOutbox()); }
    private static final class NoopOutbox implements DurableEventOutbox { public void publish(DurableEvent event) { } }
    private static final class FakeGateway implements QuarryGateway {
        BackupResult backup = new BackupResult(Path.of("/tmp/quarry-backup"), true, "ok"); Set<UUID> remainingPlayers = Set.of();
        boolean unlocked; int saveCalls, backupCalls, unloadCalls, regenerateCalls;
        public boolean isFallbackDestinationAvailable() { return true; } public boolean lockEntry() { return true; } public void unlockEntry() { unlocked = true; }
        public Collection<UUID> playersInQuarry() { return remainingPlayers; } public boolean teleportToFallback(UUID id) { return true; }
        public boolean saveQuarry() { saveCalls++; return true; } public BackupResult createBackup() { backupCalls++; return backup; }
        public boolean unloadQuarry() { unloadCalls++; return true; } public boolean regenerateQuarry() { regenerateCalls++; return true; }
        public boolean loadQuarry() { return true; } public boolean validateQuarry() { return true; }
    }
}
