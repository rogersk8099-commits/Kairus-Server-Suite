package network.neonnexus.smp.admin.moderation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime enforcement cache. Production composition should persist moderation actions through phase-1 PostgreSQL. */
public final class ModerationState {
    private final Map<UUID, Instant> frozen = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> muted = new ConcurrentHashMap<>();
    public void freeze(UUID target) { frozen.put(target, Instant.MAX); }
    public void unfreeze(UUID target) { frozen.remove(target); }
    public boolean frozen(UUID target) { return active(frozen, target); }
    public void mute(UUID target, Instant expiresAt) { muted.put(target, expiresAt); }
    public void unmute(UUID target) { muted.remove(target); }
    public boolean muted(UUID target) { return active(muted, target); }
    private boolean active(Map<UUID, Instant> map, UUID target) {
        Instant until = map.get(target);
        if (until == null) return false;
        if (Instant.now().isAfter(until)) { map.remove(target, until); return false; }
        return true;
    }
}
