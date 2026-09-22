package gg.neonnexus.smpplatform.phase3.points;

import gg.neonnexus.smpplatform.phase3.common.Actor;
import gg.neonnexus.smpplatform.phase3.common.AuditSink;
import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.common.TransactionRunner;
import gg.neonnexus.smpplatform.phase3.events.Phase3EventPublisher;
import gg.neonnexus.smpplatform.phase3.world.NeonWorld;
import gg.neonnexus.smpplatform.phase3.world.WorldPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static gg.neonnexus.smpplatform.phase3.points.PointsDomain.*;

/** Points application service. Run blocking calls off Paper's primary thread. */
public final class PointsService {
    private final PointsRepository repository; private final TransactionRunner transactions; private final WorldPolicy policy;
    private final Clock clock; private final Phase3EventPublisher events; private final AuditSink audit; private final boolean allowNegativeBalances;
    public PointsService(PointsRepository repository, TransactionRunner transactions, WorldPolicy policy, Clock clock, Phase3EventPublisher events, AuditSink audit, boolean allowNegativeBalances) {
        this.repository = Objects.requireNonNull(repository); this.transactions = Objects.requireNonNull(transactions); this.policy = Objects.requireNonNull(policy); this.clock = Objects.requireNonNull(clock);
        this.events = events == null ? Phase3EventPublisher.NOOP : events; this.audit = audit == null ? AuditSink.NOOP : audit; this.allowNegativeBalances = allowNegativeBalances;
    }
    public long balance(UUID playerId, String currencyId) {
        return transactions.required(() -> repository.findAccount(new AccountKey(OwnerType.PLAYER, playerId, currencyId)).map(PointAccount::balance).orElse(0L));
    }
    public PointTransaction award(Actor actor, UUID playerId, String currencyId, long amount, Source source, String reason, Map<String, String> metadata) { require(amount > 0, Phase3Exception.Code.INVALID_ARGUMENT, "amount must be positive"); return mutate(actor, new AccountKey(OwnerType.PLAYER, playerId, currencyId), amount, source, reason, metadata, false); }
    /** Awards a configured source once per player, using a durable idempotency reservation. */
    public java.util.Optional<PointTransaction> awardOnce(Actor actor, UUID playerId, String rewardKey, String currencyId, long amount, Source source, String reason, Map<String, String> metadata) {
        require(amount > 0, Phase3Exception.Code.INVALID_ARGUMENT, "amount must be positive");
        AccountKey key = new AccountKey(OwnerType.PLAYER, playerId, currencyId);
        require(policy.permitsCurrency(actor.world(), key.currencyId()), Phase3Exception.Code.POLICY_DENIED, key.currencyId()+" is unavailable in "+actor.world().displayName());
        PointTransaction transaction = transactions.required(() -> {
            if (!repository.claimAutomaticReward(rewardKey, playerId, clock.instant())) return null;
            return repository.applyAtomically(new BalanceChange(key, amount, source, reason, metadata, actor.world(), actor.playerId(), UUID.randomUUID()), allowNegativeBalances, clock.instant());
        });
        if (transaction != null) events.pointsChanged(transaction);
        return java.util.Optional.ofNullable(transaction);
    }
    public PointTransaction remove(Actor actor, UUID playerId, String currencyId, long amount, String reason, Map<String, String> metadata) { requireAdmin(actor); require(amount > 0, Phase3Exception.Code.INVALID_ARGUMENT, "amount must be positive"); return mutate(actor, new AccountKey(OwnerType.PLAYER, playerId, currencyId), -amount, Source.ADMIN, reason, metadata, true); }
    /**
     * Charges the actor's own player account for a server-owned gameplay action.  Unlike
     * {@link #remove}, this deliberately does not grant administrative spending power to the
     * caller.  The repository's atomic balance update rejects an insufficient balance.
     */
    public PointTransaction spend(Actor actor, UUID playerId, String currencyId, long amount, Source source, String reason, Map<String, String> metadata) {
        require(actor.playerId().equals(playerId), Phase3Exception.Code.FORBIDDEN, "You can only spend points from your own account");
        require(amount > 0, Phase3Exception.Code.INVALID_ARGUMENT, "amount must be positive");
        return mutate(actor, new AccountKey(OwnerType.PLAYER, playerId, currencyId), -amount, source, reason, metadata, false);
    }
    public PointTransaction add(Actor actor, UUID playerId, String currencyId, long amount, String reason, Map<String, String> metadata) { requireAdmin(actor); return award(actor, playerId, currencyId, amount, Source.ADMIN, reason, metadata); }
    public PointTransaction set(Actor actor, UUID playerId, String currencyId, long targetBalance, String reason, Map<String, String> metadata) {
        requireAdmin(actor); require(targetBalance >= 0 || allowNegativeBalances, Phase3Exception.Code.INVALID_ARGUMENT, "negative balances are disabled");
        AccountKey key = new AccountKey(OwnerType.PLAYER, playerId, currencyId);
        require(policy.permitsCurrency(actor.world(), key.currencyId()), Phase3Exception.Code.POLICY_DENIED, key.currencyId() + " is unavailable in " + actor.world().displayName());
        PointTransaction transaction = transactions.required(() -> {
            long current = repository.findAccount(key).map(PointAccount::balance).orElse(0L);
            long delta = Math.subtractExact(targetBalance, current);
            if (delta == 0) throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT, "new balance equals current balance");
            return repository.applyAtomically(new BalanceChange(key, delta, Source.ADMIN, reason, metadata, actor.world(), actor.playerId(), UUID.randomUUID()), allowNegativeBalances, clock.instant());
        });
        events.pointsChanged(transaction);
        audit.record(new AuditSink.AuditEntry(actor.playerId(), "POINTS_SET", key.ownerId().toString(), actor.world(), Map.of("currency", key.currencyId(), "targetBalance", Long.toString(targetBalance), "reason", reason), clock.instant(), transaction.correlationId()));
        return transaction;
    }
    public PointAccount inspect(Actor actor, UUID playerId, String currencyId) { requireAdmin(actor); return transactions.required(() -> repository.findAccount(new AccountKey(OwnerType.PLAYER, playerId, currencyId)).orElseThrow(() -> new Phase3Exception(Phase3Exception.Code.NOT_FOUND, "Points account not found"))); }
    public List<PointTransaction> history(Actor actor, UUID playerId, String currencyId, int limit, Instant before) { if (!actor.playerId().equals(playerId) && !actor.isPointsAdmin()) throw new Phase3Exception(Phase3Exception.Code.FORBIDDEN, "Cannot view another player's point history"); return transactions.required(() -> repository.history(new AccountKey(OwnerType.PLAYER, playerId, currencyId), limit(limit), before == null ? Instant.ofEpochSecond(253402300799L) : before)); }
    public List<LeaderboardEntry> top(String currencyId, OwnerType type, int limit) { return transactions.required(() -> repository.leaderboard(requireCurrency(currencyId), type, limit(limit))); }
    public PointTransaction awardGuild(Actor actor, UUID guildId, String currencyId, long amount, Source source, String reason, Map<String, String> metadata) { require(amount > 0, Phase3Exception.Code.INVALID_ARGUMENT, "amount must be positive"); return mutate(actor, new AccountKey(OwnerType.GUILD, guildId, currencyId), amount, source, reason, metadata, false); }
    private PointTransaction mutate(Actor actor, AccountKey key, long delta, Source source, String reason, Map<String, String> metadata, boolean privileged) {
        if (privileged) requireAdmin(actor); require(policy.permitsCurrency(actor.world(), key.currencyId()), Phase3Exception.Code.POLICY_DENIED, key.currencyId() + " is unavailable in " + actor.world().displayName());
        BalanceChange change = new BalanceChange(key, delta, source, reason, metadata, actor.world(), actor.playerId(), UUID.randomUUID());
        PointTransaction transaction = transactions.required(() -> repository.applyAtomically(change, allowNegativeBalances, clock.instant()));
        events.pointsChanged(transaction);
        if (source == Source.ADMIN) audit.record(new AuditSink.AuditEntry(actor.playerId(), delta > 0 ? "POINTS_ADDED" : "POINTS_REMOVED", key.ownerId().toString(), actor.world(), Map.of("currency", key.currencyId(), "amount", Long.toString(delta), "reason", reason), clock.instant(), transaction.correlationId()));
        return transaction;
    }
    private static int limit(int limit) { if (limit < 1 || limit > 100) throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT, "Limit must be 1-100"); return limit; }
    private static void requireAdmin(Actor actor) { if (!actor.isPointsAdmin()) throw new Phase3Exception(Phase3Exception.Code.FORBIDDEN, "Missing smpplatform.admin.points"); }
    private static void require(boolean value, Phase3Exception.Code code, String message) { if (!value) throw new Phase3Exception(code, message); }
}
