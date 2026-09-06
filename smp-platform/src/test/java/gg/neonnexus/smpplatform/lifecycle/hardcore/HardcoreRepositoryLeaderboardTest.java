package gg.neonnexus.smpplatform.lifecycle.hardcore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HardcoreRepositoryLeaderboardTest {
    @Test void leaderboardRepositoryOrdersHardcorePointsAndAssignsRanks() {
        InMemoryHardcoreRepository repository = new InMemoryHardcoreRepository();
        UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
        repository.savePlayer(new HardcorePlayer(first, "Season 7", HardcoreState.ALIVE, Instant.EPOCH, Instant.EPOCH));
        repository.savePlayer(new HardcorePlayer(second, "Season 7", HardcoreState.SPECTATING, Instant.EPOCH, Instant.EPOCH));
        repository.save(first, "Season 7", new HardcoreStatistics(0, 0, 0, 0, 0, 0, 0, 0, 15, 0));
        repository.save(second, "Season 7", new HardcoreStatistics(0, 0, 1, 0, 0, 0, 0, 0, 40, 0));
        List<HardcoreLeaderboardEntry> entries = repository.top("Season 7", 10);
        assertEquals(2, entries.size());
        assertEquals(second, entries.getFirst().playerId());
        assertEquals(1, entries.getFirst().rank());
        assertEquals(2, entries.get(1).rank());
    }
}
