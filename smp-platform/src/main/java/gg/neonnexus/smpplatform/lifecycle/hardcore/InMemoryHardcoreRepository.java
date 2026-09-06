package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe test adapter; replace with a transactionally consistent PostgreSQL adapter in production. */
public final class InMemoryHardcoreRepository implements HardcoreRepository, HardcoreStatisticsRepository, HardcoreLeaderboardRepository {
    private final Map<String, HardcorePlayer> players = new HashMap<>();
    private final Map<String, HardcoreStatistics> statistics = new HashMap<>();
    private final List<HardcoreDeathRecord> deaths = new ArrayList<>();
    private static String key(UUID playerId, String season) { return playerId + ":" + season; }

    @Override public synchronized Optional<HardcorePlayer> find(UUID playerId, String season) { return Optional.ofNullable(players.get(key(playerId, season))); }
    @Override public synchronized List<HardcorePlayer> findByState(String season, HardcoreState state) {
        return players.values().stream().filter(p -> p.season().equals(season) && p.state() == state).toList();
    }
    @Override public synchronized void savePlayer(HardcorePlayer player) { players.put(key(player.playerId(), player.season()), player); }
    @Override public synchronized void appendDeath(HardcoreDeathRecord death) { deaths.add(death); }
    @Override public synchronized Optional<HardcoreStatistics> findStatistics(UUID playerId, String season) { return Optional.ofNullable(statistics.get(key(playerId, season))); }
    @Override public synchronized void save(UUID playerId, String season, HardcoreStatistics value) { statistics.put(key(playerId, season), value); }
    @Override public synchronized List<HardcoreLeaderboardEntry> top(String season, int limit) {
        List<HardcorePlayer> ordered = players.values().stream().filter(p -> p.season().equals(season))
                .sorted(java.util.Comparator.comparingLong((HardcorePlayer p) -> statistics.getOrDefault(key(p.playerId(), season), HardcoreStatistics.empty()).hardcorePoints()).reversed()
                        .thenComparing(HardcorePlayer::playerId))
                .limit(Math.max(0, limit)).toList();
        java.util.ArrayList<HardcoreLeaderboardEntry> result = new java.util.ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            HardcorePlayer player = ordered.get(index);
            result.add(new HardcoreLeaderboardEntry(index + 1, player.playerId(), statistics.getOrDefault(key(player.playerId(), season), HardcoreStatistics.empty()), player.state()));
        }
        return List.copyOf(result);
    }
    public synchronized List<HardcoreDeathRecord> deaths() { return List.copyOf(deaths); }
}
