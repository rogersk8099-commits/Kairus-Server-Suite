package com.neonnexus.smpplatform.world;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Single authority for Minecraft-side world configuration. Remote sync is monotonic by revision;
 * local cache keeps gameplay routing possible while the Central API is unavailable.
 */
public final class WorldRegistry {
    private final WorldRegistryCache cache;
    private final Clock clock;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private State state;

    public WorldRegistry(RegistryDocument initial, WorldRegistryCache cache, Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
        validate(initial.worlds());
        this.state = State.from(initial, null, false, null, null);
    }

    /** Loads newest valid cached snapshot or remains on declared defaults in explicit offline mode. */
    public void bootstrapFromCache() {
        cache.load().ifPresentOrElse(cached -> {
            try {
                applyRemote(cached.document(), cached.lastSyncAt());
            } catch (RuntimeException invalid) {
                setOffline("World Registry cache rejected: " + invalid.getMessage());
            }
        }, () -> setOffline("No World Registry cache available; using bundled Neon Nexus defaults"));
    }

    public RegistryApplyResult applyRemote(RegistryDocument remote, Instant syncedAt) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(syncedAt, "syncedAt");
        validate(remote.worlds());
        lock.writeLock().lock();
        try {
            if (remote.revision() < state.revision) {
                state = state.withConflict("Rejected stale World Registry revision " + remote.revision() + "; local is " + state.revision);
                return RegistryApplyResult.REJECTED_STALE_REVISION;
            }
            Map<String, WorldDefinition> candidate = byId(remote.worlds());
            if (remote.revision() == state.revision) {
                if (!candidate.equals(state.worlds)) {
                    state = state.withConflict("Revision " + remote.revision() + " had conflicting content; retained local snapshot");
                    return RegistryApplyResult.CONFLICT_SAME_REVISION;
                }
                state = state.withSync(syncedAt);
                persist(remote, syncedAt);
                return RegistryApplyResult.REFRESHED_SAME_REVISION;
            }
            state = State.from(remote, syncedAt, false, null, null);
            persist(remote, syncedAt);
            return RegistryApplyResult.APPLIED;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setOffline(String reason) {
        lock.writeLock().lock();
        try {
            state = state.withOffline(Objects.requireNonNullElse(reason, "Central API unavailable"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearOffline() {
        lock.writeLock().lock();
        try {
            state = state.withOnline();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<WorldDefinition> find(String id) {
        lock.readLock().lock();
        try { return Optional.ofNullable(state.worlds.get(id)); }
        finally { lock.readLock().unlock(); }
    }

    public WorldDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown Neon Nexus world: " + id));
    }

    public WorldRegistrySnapshot snapshot() {
        lock.readLock().lock();
        try { return new WorldRegistrySnapshot(state.revision, state.lastSyncAt, state.offline, state.offlineReason, state.conflict, state.worlds); }
        finally { lock.readLock().unlock(); }
    }

    private void persist(RegistryDocument document, Instant at) {
        try {
            cache.save(new WorldRegistryCache.CachedRegistry(document, at));
        } catch (IOException exception) {
            // Registry remains live; missing cache persistence must not make a running server unavailable.
            state = state.withConflict("Could not persist World Registry cache: " + exception.getMessage());
        }
    }

    private static Map<String, WorldDefinition> byId(Collection<WorldDefinition> worlds) {
        Map<String, WorldDefinition> result = new LinkedHashMap<>();
        for (WorldDefinition world : worlds) {
            if (result.put(world.id(), world) != null) throw new IllegalArgumentException("Duplicate world id: " + world.id());
        }
        return Map.copyOf(result);
    }

    private static void validate(Collection<WorldDefinition> worlds) {
        Map<String, WorldDefinition> map = byId(worlds);
        for (String required : DefaultWorlds.REQUIRED_IDS) {
            if (!map.containsKey(required)) throw new IllegalArgumentException("Missing required Neon Nexus world " + required);
        }
    }

    private record State(long revision, Instant lastSyncAt, boolean offline, String offlineReason, String conflict,
                         Map<String, WorldDefinition> worlds) {
        static State from(RegistryDocument document, Instant at, boolean offline, String reason, String conflict) {
            return new State(document.revision(), at, offline, reason, conflict, byId(document.worlds()));
        }
        State withSync(Instant at) { return new State(revision, at, false, null, conflict, worlds); }
        State withConflict(String value) { return new State(revision, lastSyncAt, offline, offlineReason, value, worlds); }
        State withOffline(String reason) { return new State(revision, lastSyncAt, true, reason, conflict, worlds); }
        State withOnline() { return new State(revision, lastSyncAt, false, null, conflict, worlds); }
    }
}
