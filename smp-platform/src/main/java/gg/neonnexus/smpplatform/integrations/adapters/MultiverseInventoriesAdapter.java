package gg.neonnexus.smpplatform.integrations.adapters;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import java.util.Objects;

/** Inventory grouping is delegated; Obsidian Gate must be configured independently from Ashfall. */
public final class MultiverseInventoriesAdapter extends SoftAdapter implements DelegatingAdapters.MultiverseInventories {
    @FunctionalInterface public interface GroupLookup { String group(NexusWorld world); }
    @FunctionalInterface public interface QuarrySharing { boolean setEnabled(boolean enabled); }
    private final GroupLookup groupLookup; private final QuarrySharing quarrySharing;
    public MultiverseInventoriesAdapter(SoftDependency dependency, GroupLookup groupLookup, QuarrySharing quarrySharing) { super(dependency); this.groupLookup = Objects.requireNonNull(groupLookup); this.quarrySharing = Objects.requireNonNull(quarrySharing); }
    @Override public AdapterResult<String> inventoryGroup(NexusWorld world) { return available() ? AdapterResult.of(groupLookup.group(world)) : unavailable(); }
    @Override public AdapterResult<Boolean> setQuarrySharesAshfall(boolean enabled) { return available() ? AdapterResult.of(quarrySharing.setEnabled(enabled)) : unavailable(); }
}
