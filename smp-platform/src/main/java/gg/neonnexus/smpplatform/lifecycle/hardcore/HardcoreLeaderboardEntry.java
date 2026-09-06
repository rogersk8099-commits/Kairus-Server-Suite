package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.util.UUID;

public record HardcoreLeaderboardEntry(int rank, UUID playerId, HardcoreStatistics statistics, HardcoreState state) { }
