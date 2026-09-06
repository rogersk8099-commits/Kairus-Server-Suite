package gg.neonnexus.smpplatform.integrations.adapters;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Claims implementation is intentionally pluggable; absent claims means the core does not fabricate ownership data. */
public final class ClaimsAdapter extends SoftAdapter implements DelegatingAdapters.Claims {
    public interface ClaimsGateway { boolean canBuild(UUID playerId, String world, int x, int y, int z); Optional<String> ownerAt(String world, int x, int y, int z); }
    private final ClaimsGateway claims;
    public ClaimsAdapter(SoftDependency dependency, ClaimsGateway claims) { super(dependency); this.claims = Objects.requireNonNull(claims); }
    @Override public AdapterResult<Boolean> canBuild(UUID playerId, String world, int x, int y, int z) { return available() ? AdapterResult.of(claims.canBuild(playerId, world, x, y, z)) : unavailable(); }
    @Override public Optional<String> ownerAt(String world, int x, int y, int z) { return available() ? claims.ownerAt(world, x, y, z) : Optional.empty(); }
}
