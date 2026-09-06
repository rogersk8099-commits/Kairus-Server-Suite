package com.neonnexus.smp.eventscreative;

/** The only valid event lifecycle, shared by gameplay, website and Discord projections. */
public enum EventLifecycle {
    DRAFT, SCHEDULED, REGISTRATION_OPEN, CHECK_IN, STARTING, LIVE, FINISHED, ARCHIVED
}
