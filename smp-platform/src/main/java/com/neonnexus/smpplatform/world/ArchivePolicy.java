package com.neonnexus.smpplatform.world;

public record ArchivePolicy(boolean tourMode, boolean blockBreakDenied, boolean blockPlaceDenied,
                            boolean containerMutationDenied, boolean terrainDamageDenied) {
    public static ArchivePolicy none() {
        return new ArchivePolicy(false, false, false, false, false);
    }

    public static ArchivePolicy strictTourMode() {
        return new ArchivePolicy(true, true, true, true, true);
    }
}
