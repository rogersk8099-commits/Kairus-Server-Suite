package com.neonnexus.smpplatform.world;

import java.util.Locale;
import java.util.Objects;

/**
 * An extensible registry type. Known values are provided for policy decisions,
 * while a future platform type can be represented without changing this class.
 */
public record WorldType(String value) {
    public static final WorldType SURVIVAL = known("survival");
    public static final WorldType HARDCORE = known("hardcore");
    public static final WorldType CREATIVE = known("creative");
    public static final WorldType EVENT = known("event");
    public static final WorldType RESOURCE = known("resource");
    public static final WorldType ARCHIVE = known("archive");

    public WorldType {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z][a-z0-9_-]{1,62}")) {
            throw new IllegalArgumentException("World type must be a stable lowercase identifier");
        }
    }

    public static WorldType of(String value) {
        return new WorldType(value);
    }

    private static WorldType known(String value) {
        return new WorldType(value);
    }
}
