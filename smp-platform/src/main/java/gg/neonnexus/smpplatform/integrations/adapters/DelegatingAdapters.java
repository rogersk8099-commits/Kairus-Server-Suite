package gg.neonnexus.smpplatform.integrations.adapters;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import java.util.Optional;
import java.util.UUID;

/** Narrow contracts keep mature plugins authoritative and prevent SMPPlatform from duplicating them. */
public final class DelegatingAdapters {
    private DelegatingAdapters() { }
    public interface Multiverse { AdapterResult<Boolean> teleport(UUID playerId, String minecraftWorldName); AdapterResult<Boolean> setAccess(NexusWorld world, boolean open); }
    public interface MultiverseInventories { AdapterResult<String> inventoryGroup(NexusWorld world); AdapterResult<Boolean> setQuarrySharesAshfall(boolean enabled); }
    public interface PlotSquared { AdapterResult<String> currentPlot(UUID playerId); AdapterResult<Boolean> submitCurrentPlot(UUID playerId, String title, String description); }
    public interface Economy { AdapterResult<Long> balance(UUID playerId, String currency); AdapterResult<Boolean> withdraw(UUID playerId, long amount, String reason); AdapterResult<Boolean> deposit(UUID playerId, long amount, String reason); }
    public interface Claims { AdapterResult<Boolean> canBuild(UUID playerId, String minecraftWorldName, int x, int y, int z); Optional<String> ownerAt(String minecraftWorldName, int x, int y, int z); }
    public static <T> AdapterResult<T> unavailable(String integration) { return AdapterResult.unavailable(integration + " is not available"); }
}
