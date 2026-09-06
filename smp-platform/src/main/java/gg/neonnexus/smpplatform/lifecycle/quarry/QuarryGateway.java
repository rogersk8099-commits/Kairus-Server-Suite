package gg.neonnexus.smpplatform.lifecycle.quarry;

import java.util.Collection;
import java.util.UUID;

/**
 * Adapter for Multiverse/Paper/filesystem operations. Bukkit access must be marshalled to the main
 * thread inside implementations; backup and filesystem copy should run on a worker thread.
 */
public interface QuarryGateway {
    boolean isFallbackDestinationAvailable();
    boolean lockEntry();
    void unlockEntry();
    Collection<UUID> playersInQuarry();
    boolean teleportToFallback(UUID playerId);
    boolean saveQuarry();
    BackupResult createBackup();
    boolean unloadQuarry();
    boolean regenerateQuarry();
    boolean loadQuarry();
    boolean validateQuarry();
}
