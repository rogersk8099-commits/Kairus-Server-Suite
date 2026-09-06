package gg.neonnexus.smpplatform.lifecycle.quarry;

import java.util.Optional;

/** Persistence boundary for world_resets and world_backups records. */
public interface QuarryResetRepository {
    void save(QuarryResetSnapshot snapshot);
    Optional<QuarryResetSnapshot> latest();
}
