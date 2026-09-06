package com.neonnexus.smpplatform.multiverse;

import com.neonnexus.smpplatform.inventory.InventoryGroupPolicy;
import com.neonnexus.smpplatform.world.WorldDefinition;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Validates hard isolation before delegating operational group setup to Multiverse-Inventories. */
public final class MultiverseInventoryAdapter {
    private final Plugin inventories; private final Logger logger;
    public MultiverseInventoryAdapter(PluginManager plugins, Logger logger) { inventories = plugins.getPlugin("Multiverse-Inventories"); this.logger = Objects.requireNonNull(logger); }
    public boolean available() { return inventories != null && inventories.isEnabled(); }
    public Map<String, String> validateDesiredGroups(Collection<WorldDefinition> worlds, InventoryGroupPolicy policy) {
        Map<String,String> groups = policy.groupsFor(worlds); if (!available()) logger.warning("Multiverse-Inventories absent; inventory group map validated but external switching is unavailable."); return groups;
    }
}
