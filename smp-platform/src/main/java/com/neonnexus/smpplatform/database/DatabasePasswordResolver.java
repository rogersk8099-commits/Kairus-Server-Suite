package com.neonnexus.smpplatform.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/** Resolves the PostgreSQL password without ever logging or returning a source-controlled default. */
final class DatabasePasswordResolver {
    enum Source { ENVIRONMENT, FILE }

    record Resolved(String value, Source source) {
        Resolved {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Database password must not be blank");
            Objects.requireNonNull(source);
        }
    }

    private DatabasePasswordResolver() { }

    static Resolved resolve(String environmentName, Path passwordFile) {
        return resolve(environmentName, passwordFile, System::getenv);
    }

    static Resolved resolve(String environmentName, Path passwordFile, Function<String, String> environment) {
        Objects.requireNonNull(environmentName);
        Objects.requireNonNull(passwordFile);
        Objects.requireNonNull(environment);
        String runtime = environment.apply(environmentName);
        if (runtime != null && !runtime.isBlank()) return new Resolved(runtime.trim(), Source.ENVIRONMENT);
        if (!Files.isRegularFile(passwordFile)) {
            throw new IllegalStateException("environment variable " + environmentName + " and protected password file are absent");
        }
        try {
            String fileValue = Files.readString(passwordFile).trim();
            if (fileValue.isBlank()) throw new IllegalStateException("protected database password file is blank");
            return new Resolved(fileValue, Source.FILE);
        } catch (IOException exception) {
            throw new IllegalStateException("protected database password file cannot be read", exception);
        }
    }
}
