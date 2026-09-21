package com.neonnexus.smpplatform.world;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class WorldRegistryTest {
    @Test void supportsKnownAndFutureWorldTypes() {
        assertEquals("hub", WorldType.HUB.value());
        assertEquals("survival", WorldType.SURVIVAL.value());
        assertEquals("seasonal-realm", WorldType.of("seasonal-realm").value());
        assertThrows(IllegalArgumentException.class, () -> WorldType.of("A Bad Type"));
    }
    @Test void acceptsNewerRevisionAndRejectsStaleOrConflictingSameRevision() throws Exception {
        WorldRegistry registry = new WorldRegistry(DefaultWorlds.document(), cache(), Clock.systemUTC());
        assertEquals(RegistryApplyResult.APPLIED, registry.applyRemote(new RegistryDocument(2, Instant.now(), DefaultWorlds.definitions()), Instant.now()));
        assertEquals(RegistryApplyResult.REJECTED_STALE_REVISION, registry.applyRemote(DefaultWorlds.document(), Instant.now()));
        List<WorldDefinition> altered = DefaultWorlds.definitions();
        WorldDefinition ashfall = altered.getFirst();
        altered = new java.util.ArrayList<>(altered);
        altered.set(0, new WorldDefinition(ashfall.id(), ashfall.minecraftWorldName(), ashfall.displayName(), "changed", ashfall.type(), ashfall.season(), ashfall.status(), ashfall.difficulty(), ashfall.borderSize(), ashfall.pvpMode(), ashfall.guildsEnabled(), ashfall.pointsEnabled(), ashfall.currencyId(), ashfall.claimsEnabled(), ashfall.economyEnabled(), ashfall.inventoryGroup(), ashfall.resetPolicy(), ashfall.archivePolicy(), ashfall.discordEnabled(), ashfall.websiteVisible(), ashfall.mapVisible(), ashfall.playerCount(), ashfall.maintenanceMode(), ashfall.accessPermission(), ashfall.spawnLocation()));
        assertEquals(RegistryApplyResult.CONFLICT_SAME_REVISION, registry.applyRemote(new RegistryDocument(2, Instant.now(), altered), Instant.now()));
        assertTrue(registry.snapshot().lastConflict().contains("conflicting"));
    }
    private WorldRegistryCache cache() throws Exception { return new WorldRegistryCache(Files.createTempDirectory("registry-test").resolve("cache.json"), Logger.getAnonymousLogger()); }
}
