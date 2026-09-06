package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.util.List;

/** Query boundary for the website and in-game Obsidian Gate leaderboard. */
public interface HardcoreLeaderboardRepository {
    List<HardcoreLeaderboardEntry> top(String season, int limit);
}
