package com.neonnexus.smpplatform.world;

import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.*;

class DefaultWorldsTest {
    @Test void preservesTheExactSixNeonNexusWorldIdsAndDisplayNames() {
        Map<String,String> names = DefaultWorlds.definitions().stream().collect(toMap(WorldDefinition::id, WorldDefinition::displayName));
        assertEquals(Map.of("ashfall","Ashfall", "obsidian-gate","Obsidian Gate", "atrium","The Atrium", "colosseum","Neon Colosseum", "quarry","The Quarry", "verdance","Verdance"), names);
    }
    @Test void encodesWebsiteResetAndTourPolicies() {
        WorldDefinition quarry = DefaultWorlds.definitions().stream().filter(w -> w.id().equals("quarry")).findFirst().orElseThrow();
        ResetPolicy.Weekly reset = assertInstanceOf(ResetPolicy.Weekly.class, quarry.resetPolicy());
        assertEquals(DayOfWeek.MONDAY, reset.day()); assertEquals(LocalTime.of(4,0), reset.time()); assertEquals(ZoneOffset.UTC, reset.timezone()); assertTrue(reset.safetyBackupRequired());
        WorldDefinition verdance = DefaultWorlds.definitions().stream().filter(w -> w.id().equals("verdance")).findFirst().orElseThrow();
        assertTrue(verdance.archivePolicy().tourMode()); assertTrue(verdance.archivePolicy().blockBreakDenied()); assertFalse(verdance.discordEnabled());
    }
}
