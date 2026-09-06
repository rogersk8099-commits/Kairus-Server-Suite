package gg.neonnexus.smpplatform.phase3.points;

import gg.neonnexus.smpplatform.phase3.world.NeonWorld;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Namespace container for points types; every mutation is represented by a PointTransaction. */
public final class PointsDomain {
    private PointsDomain() { }
    private static final Pattern CURRENCY = Pattern.compile("[A-Z][A-Z0-9_]{1,47}");

    public enum OwnerType { PLAYER, GUILD }
    public enum Source { GAMEPLAY, ADMIN, ACHIEVEMENT, EVENT, GUILD, QUEST, SKRIPT, API, SYSTEM, REFUND, ADJUSTMENT }

    public record AccountKey(OwnerType ownerType, UUID ownerId, String currencyId) {
        public AccountKey {
            Objects.requireNonNull(ownerType, "ownerType"); Objects.requireNonNull(ownerId, "ownerId");
            currencyId = requireCurrency(currencyId);
        }
    }
    public record PointAccount(UUID id, AccountKey key, long balance, long version, Instant createdAt, Instant updatedAt) {
        public PointAccount { Objects.requireNonNull(id, "id"); Objects.requireNonNull(key, "key"); Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt"); if (version < 0) throw new IllegalArgumentException("version"); }
    }
    public record PointTransaction(UUID id, UUID accountId, String currencyId, long amount, long balanceBefore,
                                   long balanceAfter, Source source, String reason, Map<String, String> metadata,
                                   NeonWorld world, UUID actorId, UUID correlationId, Instant occurredAt) {
        public PointTransaction {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(accountId, "accountId"); currencyId = requireCurrency(currencyId);
            Objects.requireNonNull(source, "source"); reason = requireReason(reason); metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
            Objects.requireNonNull(correlationId, "correlationId"); Objects.requireNonNull(occurredAt, "occurredAt");
            if (balanceAfter != Math.addExact(balanceBefore, amount)) throw new IllegalArgumentException("transaction arithmetic mismatch");
        }
    }
    public record BalanceChange(AccountKey account, long amount, Source source, String reason, Map<String, String> metadata,
                                NeonWorld world, UUID actorId, UUID correlationId) {
        public BalanceChange { Objects.requireNonNull(account, "account"); Objects.requireNonNull(source, "source"); reason = requireReason(reason); metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata")); Objects.requireNonNull(correlationId, "correlationId"); if (amount == 0) throw new IllegalArgumentException("amount must be non-zero"); }
    }
    public record LeaderboardEntry(AccountKey account, long balance, int rank) { }

    public static String requireCurrency(String input) {
        String value = Objects.requireNonNull(input, "currencyId").trim().toUpperCase(java.util.Locale.ROOT);
        if (!CURRENCY.matcher(value).matches()) throw new IllegalArgumentException("currencyId must be 2-48 uppercase safe characters");
        return value;
    }
    private static String requireReason(String input) {
        String value = Objects.requireNonNull(input, "reason").trim();
        if (value.isEmpty() || value.length() > 256) throw new IllegalArgumentException("reason must be 1-256 characters");
        return value;
    }
}
