package gg.neonnexus.smpplatform.integrations.adapters;

import java.util.Objects;
import java.util.UUID;

/** Vault is optional and authoritative only for configured external money, not Nexus points transactions. */
public final class VaultEconomyAdapter extends SoftAdapter implements DelegatingAdapters.Economy {
    public interface EconomyGateway { long balance(UUID playerId, String currency); boolean withdraw(UUID playerId, long amount, String reason); boolean deposit(UUID playerId, long amount, String reason); }
    private final EconomyGateway economy;
    public VaultEconomyAdapter(SoftDependency dependency, EconomyGateway economy) { super(dependency); this.economy = Objects.requireNonNull(economy); }
    @Override public AdapterResult<Long> balance(UUID playerId, String currency) { return available() ? AdapterResult.of(economy.balance(playerId, currency)) : unavailable(); }
    @Override public AdapterResult<Boolean> withdraw(UUID playerId, long amount, String reason) { if (amount < 0) throw new IllegalArgumentException("amount must be non-negative"); return available() ? AdapterResult.of(economy.withdraw(playerId, amount, reason)) : unavailable(); }
    @Override public AdapterResult<Boolean> deposit(UUID playerId, long amount, String reason) { if (amount < 0) throw new IllegalArgumentException("amount must be non-negative"); return available() ? AdapterResult.of(economy.deposit(playerId, amount, reason)) : unavailable(); }
}
