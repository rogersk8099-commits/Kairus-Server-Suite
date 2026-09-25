package com.neonnexus.smpplatform.world;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class WorldRegistryOfflineCacheTest {
    @Test void startsFromTypedDiskCacheWhenCentralApiIsUnavailable() throws Exception {
        var path = Files.createTempDirectory("registry-cache").resolve("worlds.json");
        var cache = new WorldRegistryCache(path, Logger.getAnonymousLogger());
        RegistryDocument document = new RegistryDocument(7, Instant.parse("2026-01-01T00:00:00Z"), DefaultWorlds.definitions());
        cache.save(new WorldRegistryCache.CachedRegistry(document, Instant.parse("2026-01-02T00:00:00Z")));
        WorldRegistry registry = new WorldRegistry(DefaultWorlds.document(), cache, Clock.systemUTC());
        registry.bootstrapFromCache(); registry.setOffline("Central API unavailable");
        assertEquals(7, registry.snapshot().revision()); assertTrue(registry.snapshot().offlineMode());
        assertEquals("The Quarry", registry.require("quarry").displayName());
        assertInstanceOf(ResetPolicy.Interval.class, registry.require("quarry").resetPolicy());
    }
}
