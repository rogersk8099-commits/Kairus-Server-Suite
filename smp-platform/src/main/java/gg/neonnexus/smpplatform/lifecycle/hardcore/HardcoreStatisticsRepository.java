package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.util.Optional;
import java.util.UUID;

/** Repository interface for player_world_stats / hardcore statistics projections. */
public interface HardcoreStatisticsRepository {
    Optional<HardcoreStatistics> findStatistics(UUID playerId, String season);
    void save(UUID playerId, String season, HardcoreStatistics statistics);
}
