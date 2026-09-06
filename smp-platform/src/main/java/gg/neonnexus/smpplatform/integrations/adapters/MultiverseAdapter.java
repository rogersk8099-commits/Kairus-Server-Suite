package gg.neonnexus.smpplatform.integrations.adapters;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import java.util.Objects;
import java.util.UUID;

/** Delegates runtime-world responsibility to Multiverse while World Registry remains the canonical Neon Nexus map. */
public final class MultiverseAdapter extends SoftAdapter implements DelegatingAdapters.Multiverse {
    @FunctionalInterface public interface Teleporter { boolean teleport(UUID playerId, String minecraftWorldName); }
    @FunctionalInterface public interface AccessController { boolean setAccess(NexusWorld world, boolean open); }
    private final Teleporter teleporter; private final AccessController access;
    public MultiverseAdapter(SoftDependency dependency, Teleporter teleporter, AccessController access) { super(dependency); this.teleporter = Objects.requireNonNull(teleporter); this.access = Objects.requireNonNull(access); }
    @Override public AdapterResult<Boolean> teleport(UUID playerId, String minecraftWorldName) { return available() ? AdapterResult.of(teleporter.teleport(playerId, minecraftWorldName)) : unavailable(); }
    @Override public AdapterResult<Boolean> setAccess(NexusWorld world, boolean open) { return available() ? AdapterResult.of(access.setAccess(world, open)) : unavailable(); }
}
