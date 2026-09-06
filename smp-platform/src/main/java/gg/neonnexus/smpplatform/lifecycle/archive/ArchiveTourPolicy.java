package gg.neonnexus.smpplatform.lifecycle.archive;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Generic world-name policy; Verdance is merely its first configured archive. */
public final class ArchiveTourPolicy {
    private final Set<String> worldNames;
    public ArchiveTourPolicy(Set<String> worldNames) { this.worldNames = Collections.unmodifiableSet(new HashSet<>(worldNames)); }
    public boolean isTourWorld(String worldName) { return worldName != null && worldNames.contains(worldName); }
    public boolean shouldCancel(String worldName, ArchiveInteraction interaction) { return isTourWorld(worldName); }
    public Set<String> worldNames() { return worldNames; }
}
