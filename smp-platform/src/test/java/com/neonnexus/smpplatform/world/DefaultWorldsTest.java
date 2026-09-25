package com.neonnexus.smpplatform.world;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.*;

class DefaultWorldsTest {
    @Test void preservesTheExactSevenKairuWorldIdsAndDisplayNames() {
        Map<String,String> names = DefaultWorlds.definitions().stream().collect(toMap(WorldDefinition::id, WorldDefinition::displayName));
        assertEquals(Map.of("spawn-hub","Spawn Hub", "ashfall","Ashfall", "obsidian-gate","Obsidian Gate", "atrium","The Atrium", "colosseum","Neon Colosseum", "quarry","The Quarry", "verdance","Verdance"), names);
        WorldDefinition hub = DefaultWorlds.definitions().stream().filter(w -> w.id().equals("spawn-hub")).findFirst().orElseThrow();
        assertEquals(WorldType.HUB, hub.type()); assertFalse(hub.guildsEnabled()); assertFalse(hub.pointsEnabled());
    }
    @Test void encodesWebsiteResetAndTourPolicies() {
        WorldDefinition quarry = DefaultWorlds.definitions().stream().filter(w -> w.id().equals("quarry")).findFirst().orElseThrow();
        ResetPolicy.Interval reset = assertInstanceOf(ResetPolicy.Interval.class, quarry.resetPolicy());
        assertEquals(Duration.ofHours(1), reset.interval()); assertTrue(reset.safetyBackupRequired());
        WorldDefinition verdance = DefaultWorlds.definitions().stream().filter(w -> w.id().equals("verdance")).findFirst().orElseThrow();
        assertTrue(verdance.archivePolicy().tourMode()); assertTrue(verdance.archivePolicy().blockBreakDenied()); assertFalse(verdance.discordEnabled());
    }
}
