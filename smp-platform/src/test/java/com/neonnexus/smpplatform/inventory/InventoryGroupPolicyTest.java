package com.neonnexus.smpplatform.inventory;

import com.neonnexus.smpplatform.world.DefaultWorlds;
import com.neonnexus.smpplatform.world.WorldDefinition;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.*;

class InventoryGroupPolicyTest {
    @Test void quarrySharesAshfallByDefaultButHardcoreNeverDoes() {
        Map<String, WorldDefinition> worlds = DefaultWorlds.definitions().stream().collect(toMap(WorldDefinition::id, value -> value));
        InventoryGroupPolicy policy = new InventoryGroupPolicy(true);
        assertTrue(policy.sharesInventory(worlds.get("ashfall"), worlds.get("quarry")));
        assertFalse(policy.sharesInventory(worlds.get("ashfall"), worlds.get("obsidian-gate")));
        assertEquals("HARDCORE_GROUP", policy.groupFor(worlds.get("obsidian-gate")));
    }
    @Test void rejectAnyAccidentalHardcoreSharingConfiguration() {
        InventoryGroupPolicy policy = new InventoryGroupPolicy(true);
        assertThrows(IllegalStateException.class, () -> policy.validate(Map.of("ashfall", "HARDCORE_GROUP", "obsidian-gate", "HARDCORE_GROUP")));
    }
    @Test void quarryCanBeSeparatedWithoutAffectingHardcore() {
        Map<String, WorldDefinition> worlds = DefaultWorlds.definitions().stream().collect(toMap(WorldDefinition::id, value -> value));
        InventoryGroupPolicy policy = new InventoryGroupPolicy(false);
        assertFalse(policy.sharesInventory(worlds.get("ashfall"), worlds.get("quarry")));
        assertFalse(policy.sharesInventory(worlds.get("quarry"), worlds.get("obsidian-gate")));
    }
}
