package com.neonnexus.smp.eventscreative;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EventResult(UUID participantId, Integer placement, int score, Map<String, Object> details) {
    public EventResult {
        Objects.requireNonNull(participantId, "participantId");
        if (placement != null && placement < 1) throw new IllegalArgumentException("Placement must be positive");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }
}

record EventReward(Integer placement, String currencyId, long amount, String description) {
    EventReward {
        if (placement != null && placement < 1) throw new IllegalArgumentException("Placement must be positive");
        if (currencyId == null || currencyId.isBlank()) throw new IllegalArgumentException("currencyId is required");
        if (amount < 0) throw new IllegalArgumentException("Reward amount cannot be negative");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
    }
}
