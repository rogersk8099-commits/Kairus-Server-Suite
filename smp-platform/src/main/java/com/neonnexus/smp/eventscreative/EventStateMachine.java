package com.neonnexus.smp.eventscreative;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/** Single lifecycle authority. There are no administrative shortcuts around validation. */
public final class EventStateMachine {
    private static final EnumMap<EventLifecycle, Set<EventLifecycle>> ALLOWED = new EnumMap<>(EventLifecycle.class);
    static {
        ALLOWED.put(EventLifecycle.DRAFT, EnumSet.of(EventLifecycle.SCHEDULED));
        ALLOWED.put(EventLifecycle.SCHEDULED, EnumSet.of(EventLifecycle.REGISTRATION_OPEN, EventLifecycle.ARCHIVED));
        ALLOWED.put(EventLifecycle.REGISTRATION_OPEN, EnumSet.of(EventLifecycle.CHECK_IN, EventLifecycle.ARCHIVED));
        ALLOWED.put(EventLifecycle.CHECK_IN, EnumSet.of(EventLifecycle.STARTING, EventLifecycle.ARCHIVED));
        ALLOWED.put(EventLifecycle.STARTING, EnumSet.of(EventLifecycle.LIVE, EventLifecycle.ARCHIVED));
        ALLOWED.put(EventLifecycle.LIVE, EnumSet.of(EventLifecycle.FINISHED));
        ALLOWED.put(EventLifecycle.FINISHED, EnumSet.of(EventLifecycle.ARCHIVED));
        ALLOWED.put(EventLifecycle.ARCHIVED, EnumSet.noneOf(EventLifecycle.class));
    }

    private EventStateMachine() { }

    public static boolean canTransition(EventLifecycle from, EventLifecycle to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    public static void requireTransition(EventLifecycle from, EventLifecycle to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid event lifecycle transition: " + from + " -> " + to);
        }
    }
}
