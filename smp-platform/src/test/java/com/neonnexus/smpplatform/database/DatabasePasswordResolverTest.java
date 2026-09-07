package com.neonnexus.smpplatform.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DatabasePasswordResolverTest {
    @TempDir Path temporaryDirectory;

    @Test
    void environmentValueTakesPrecedenceOverFile() throws Exception {
        Path file = temporaryDirectory.resolve("database-password.txt");
        Files.writeString(file, "file-secret\n");

        var resolved = DatabasePasswordResolver.resolve("SMPPLATFORM_DB_PASSWORD", file, ignored -> "environment-secret");

        assertEquals("environment-secret", resolved.value());
        assertEquals(DatabasePasswordResolver.Source.ENVIRONMENT, resolved.source());
    }

    @Test
    void protectedFileIsUsedWhenEnvironmentIsUnavailable() throws Exception {
        Path file = temporaryDirectory.resolve("database-password.txt");
        Files.writeString(file, "  file-secret  \n");

        var resolved = DatabasePasswordResolver.resolve("SMPPLATFORM_DB_PASSWORD", file, ignored -> null);

        assertEquals("file-secret", resolved.value());
        assertEquals(DatabasePasswordResolver.Source.FILE, resolved.source());
    }

    @Test
    void missingEnvironmentAndFileFailClosed() {
        Path file = temporaryDirectory.resolve("missing.txt");

        assertThrows(IllegalStateException.class,
                () -> DatabasePasswordResolver.resolve("SMPPLATFORM_DB_PASSWORD", file, ignored -> null));
    }
}
