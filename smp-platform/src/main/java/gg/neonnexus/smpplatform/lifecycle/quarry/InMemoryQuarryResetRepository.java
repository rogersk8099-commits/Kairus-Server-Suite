package gg.neonnexus.smpplatform.lifecycle.quarry;

import java.util.Optional;

public final class InMemoryQuarryResetRepository implements QuarryResetRepository {
    private QuarryResetSnapshot latest;
    @Override public synchronized void save(QuarryResetSnapshot snapshot) { latest = snapshot; }
    @Override public synchronized Optional<QuarryResetSnapshot> latest() { return Optional.ofNullable(latest); }
}
