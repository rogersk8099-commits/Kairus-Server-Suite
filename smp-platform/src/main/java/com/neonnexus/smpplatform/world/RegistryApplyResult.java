package com.neonnexus.smpplatform.world;

public enum RegistryApplyResult {
    APPLIED,
    REFRESHED_SAME_REVISION,
    REJECTED_STALE_REVISION,
    CONFLICT_SAME_REVISION
}
