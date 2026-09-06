package com.neonnexus.smp.eventscreative;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical public world identifiers; never infer or rename these values in feature modules. */
public final class CanonicalWorlds {
    public static final String ASHFALL = "ashfall";
    public static final String OBSIDIAN_GATE = "obsidian-gate";
    public static final String ATRIUM = "atrium";
    public static final String COLOSSEUM = "colosseum";
    public static final String QUARRY = "quarry";
    public static final String VERDANCE = "verdance";

    private static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry(ASHFALL, "Ashfall"),
        Map.entry(OBSIDIAN_GATE, "Obsidian Gate"),
        Map.entry(ATRIUM, "The Atrium"),
        Map.entry(COLOSSEUM, "Neon Colosseum"),
        Map.entry(QUARRY, "The Quarry"),
        Map.entry(VERDANCE, "Verdance")
    );

    private CanonicalWorlds() { }

    public static String requireId(String id) {
        if (!NAMES.containsKey(id)) throw new IllegalArgumentException("Unknown Neon Nexus world id: " + id);
        return id;
    }

    public static String displayName(String id) { return NAMES.get(requireId(id)); }

    public static void require(String id, String expected) {
        Objects.requireNonNull(id, "worldId");
        if (!expected.equals(id)) throw new IllegalArgumentException("This module is scoped to " + expected + ", not " + id);
    }

    public static Map<String, String> all() { return new LinkedHashMap<>(NAMES); }
}
