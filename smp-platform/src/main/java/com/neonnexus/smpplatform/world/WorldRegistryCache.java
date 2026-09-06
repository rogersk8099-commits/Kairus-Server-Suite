package com.neonnexus.smpplatform.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Logger;

/** Disk cache is an availability mechanism, never a replacement for PostgreSQL gameplay data. */
public final class WorldRegistryCache {
    private final Path file;
    private final WorldRegistryJsonCodec codec = new WorldRegistryJsonCodec();
    private final Logger logger;

    public WorldRegistryCache(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Optional<CachedRegistry> load() {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(codec.decodeCache(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            logger.warning("Ignoring unreadable World Registry cache " + file + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    public void save(CachedRegistry cache) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, codec.encodeCache(cache.document(), cache.lastSyncAt()), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record CachedRegistry(RegistryDocument document, java.time.Instant lastSyncAt) { }
}
