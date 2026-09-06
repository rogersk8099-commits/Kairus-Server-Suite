package com.neonnexus.smpplatform.inventory;

import com.neonnexus.smpplatform.world.WorldDefinition;
import com.neonnexus.smpplatform.world.WorldType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Guards inventory isolation independently of optional Multiverse-Inventories availability. */
public final class InventoryGroupPolicy {
    private final boolean shareQuarryWithAshfall;

    public InventoryGroupPolicy(boolean shareQuarryWithAshfall) {
        this.shareQuarryWithAshfall = shareQuarryWithAshfall;
    }

    public String groupFor(WorldDefinition world) {
        Objects.requireNonNull(world, "world");
        if (world.type().equals(WorldType.HARDCORE)) return "HARDCORE_GROUP";
        if (world.id().equals("quarry")) return shareQuarryWithAshfall ? "ASHFALL_GROUP" : "RESOURCE_GROUP";
        return world.inventoryGroup();
    }

    public boolean sharesInventory(WorldDefinition first, WorldDefinition second) {
        return groupFor(first).equals(groupFor(second));
    }

    public Map<String, String> groupsFor(Iterable<WorldDefinition> worlds) {
        Map<String, String> groups = new LinkedHashMap<>();
        for (WorldDefinition world : worlds) groups.put(world.id(), groupFor(world));
        validate(groups);
        return Map.copyOf(groups);
    }

    /** Hardcore is a hard safety boundary even if an operator misconfigures other groups. */
    public void validate(Map<String, String> groups) {
        String hardcore = groups.get("obsidian-gate");
        if (hardcore != null) {
            for (Map.Entry<String, String> entry : groups.entrySet()) {
                if (!entry.getKey().equals("obsidian-gate") && hardcore.equals(entry.getValue())) {
                    throw new IllegalStateException("Obsidian Gate inventory group must never be shared with " + entry.getKey());
                }
            }
        }
    }
}
