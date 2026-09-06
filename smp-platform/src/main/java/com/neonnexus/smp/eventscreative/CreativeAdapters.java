package com.neonnexus.smp.eventscreative;

import java.util.Optional;
import java.util.UUID;

/** Optional PlotSquared facade. Plot ownership and plot mechanics remain PlotSquared's source of truth. */
interface PlotSquaredAdapter {
    boolean available();
    Optional<PlotReference> plotAt(String worldId, double x, double y, double z);
    boolean isOwnerOrTrusted(UUID playerId, PlotReference plot);
    record PlotReference(String plotId, String worldId, String displayName) { }
}

/** Optional WorldEdit facade. Permission policy belongs to LuckPerms; this adapter only exposes capability. */
interface WorldEditAdapter {
    boolean available();
    boolean canUseWorldEdit(UUID playerId, String worldId);
}

final class UnavailablePlotSquaredAdapter implements PlotSquaredAdapter {
    @Override public boolean available() { return false; }
    @Override public Optional<PlotReference> plotAt(String worldId, double x, double y, double z) { return Optional.empty(); }
    @Override public boolean isOwnerOrTrusted(UUID playerId, PlotReference plot) { return false; }
}

final class UnavailableWorldEditAdapter implements WorldEditAdapter {
    @Override public boolean available() { return false; }
    @Override public boolean canUseWorldEdit(UUID playerId, String worldId) { return false; }
}
