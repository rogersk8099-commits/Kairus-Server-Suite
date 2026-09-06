package com.neonnexus.smpplatform.world;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RegistryDocument(long revision, Instant generatedAt, List<WorldDefinition> worlds) {
    public RegistryDocument {
        if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        worlds = List.copyOf(Objects.requireNonNull(worlds, "worlds"));
    }
}
