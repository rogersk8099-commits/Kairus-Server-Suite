package gg.neonnexus.smpplatform.phase3.points;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static gg.neonnexus.smpplatform.phase3.points.PointsDomain.*;

/**
 * Blocking persistence port. applyAtomically must update account balance/version and append the
 * immutable ledger entry in one database transaction, or make neither change visible.
 */
public interface PointsRepository {
    PointTransaction applyAtomically(BalanceChange change, boolean allowNegativeBalances, Instant now);
    Optional<PointAccount> findAccount(AccountKey key);
    List<PointTransaction> history(AccountKey key, int limit, Instant beforeExclusive);
    List<LeaderboardEntry> leaderboard(String currencyId, OwnerType ownerType, int limit);
    /** Atomically reserves a configured automatic-reward key before its ledger mutation. */
    boolean claimAutomaticReward(String rewardKey, UUID playerId, Instant claimedAt);
}
