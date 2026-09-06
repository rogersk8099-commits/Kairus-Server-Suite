package gg.neonnexus.smpplatform.lifecycle.archive;

import java.util.Map;
import java.util.Set;

/** Registry slice for current and future archived worlds; no Java class is special-cased to Verdance. */
public final class ArchiveWorldRegistry {
    private final Map<String, String> worldIdByMinecraftName;
    public ArchiveWorldRegistry(Map<String, String> worldIdByMinecraftName) { this.worldIdByMinecraftName = Map.copyOf(worldIdByMinecraftName); }
    public ArchiveTourPolicy tourPolicy() { return new ArchiveTourPolicy(worldIdByMinecraftName.keySet()); }
    public Set<String> minecraftWorldNames() { return worldIdByMinecraftName.keySet(); }
    public String worldId(String minecraftName) { return worldIdByMinecraftName.get(minecraftName); }
}
