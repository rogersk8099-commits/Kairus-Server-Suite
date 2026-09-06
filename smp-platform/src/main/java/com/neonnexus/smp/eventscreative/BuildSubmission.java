package com.neonnexus.smp.eventscreative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BuildSubmission {
    private final UUID id;
    private final UUID submitterId;
    private final String worldId;
    private final String plotId;
    private final String title;
    private final String description;
    private final List<String> imageReferences;
    private final Instant submittedAt;
    private SubmissionState state;
    private UUID reviewerId;
    private String reviewNote;
    private Instant reviewedAt;
    private long revision;

    public BuildSubmission(UUID id, UUID submitterId, String worldId, String plotId, String title, String description,
                           List<String> imageReferences, Instant submittedAt, SubmissionState state, UUID reviewerId,
                           String reviewNote, Instant reviewedAt, long revision) {
        this.id = Objects.requireNonNull(id, "id"); this.submitterId = Objects.requireNonNull(submitterId, "submitterId");
        CanonicalWorlds.require(worldId, CanonicalWorlds.ATRIUM); this.worldId = worldId;
        this.plotId = required(plotId, "plotId"); this.title = required(title, "title"); this.description = required(description, "description");
        this.imageReferences = List.copyOf(Objects.requireNonNull(imageReferences, "imageReferences"));
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt"); this.state = Objects.requireNonNull(state, "state");
        this.reviewerId = reviewerId; this.reviewNote = reviewNote; this.reviewedAt = reviewedAt; this.revision = revision;
    }
    public UUID id() { return id; } public UUID submitterId() { return submitterId; } public String worldId() { return worldId; }
    public String plotId() { return plotId; } public String title() { return title; } public String description() { return description; }
    public List<String> imageReferences() { return imageReferences; } public Instant submittedAt() { return submittedAt; }
    public SubmissionState state() { return state; } public UUID reviewerId() { return reviewerId; } public String reviewNote() { return reviewNote; }
    public Instant reviewedAt() { return reviewedAt; } public long revision() { return revision; }

    public void review(UUID reviewer, String note, Instant at) { transition(SubmissionState.REVIEWED, reviewer, note, at); }
    public void reject(UUID reviewer, String reason, Instant at) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A rejection reason is required");
        transition(SubmissionState.REJECTED, reviewer, reason, at);
    }
    public void feature(UUID reviewer, String note, Instant at) { transition(SubmissionState.FEATURED, reviewer, note, at); }
    public void withdraw() {
        if (state != SubmissionState.PENDING) throw new IllegalStateException("Only pending submissions can be withdrawn");
        state = SubmissionState.WITHDRAWN; revision++;
    }
    private void transition(SubmissionState target, UUID reviewer, String note, Instant at) {
        if (!SubmissionStateMachine.canTransition(state, target)) throw new IllegalStateException("Invalid submission transition: " + state + " -> " + target);
        reviewerId = Objects.requireNonNull(reviewer, "reviewer"); reviewNote = note; reviewedAt = Objects.requireNonNull(at, "reviewedAt"); state = target; revision++;
    }
    private static String required(String value, String label) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required"); return value; }
}

enum SubmissionState { PENDING, REVIEWED, FEATURED, REJECTED, WITHDRAWN }

final class SubmissionStateMachine {
    private SubmissionStateMachine() { }
    static boolean canTransition(SubmissionState from, SubmissionState to) {
        return (from == SubmissionState.PENDING && (to == SubmissionState.REVIEWED || to == SubmissionState.REJECTED))
            || (from == SubmissionState.REVIEWED && (to == SubmissionState.FEATURED || to == SubmissionState.REJECTED));
    }
}

record FeaturedBuild(UUID id, UUID submissionId, String worldId, String plotId, Instant featuredAt, UUID featuredBy, int showcaseOrder) {
    FeaturedBuild {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(submissionId, "submissionId");
        CanonicalWorlds.require(worldId, CanonicalWorlds.ATRIUM); if (plotId == null || plotId.isBlank()) throw new IllegalArgumentException("plotId required");
        Objects.requireNonNull(featuredAt, "featuredAt"); Objects.requireNonNull(featuredBy, "featuredBy");
        if (showcaseOrder < 0) throw new IllegalArgumentException("showcaseOrder cannot be negative");
    }
}
