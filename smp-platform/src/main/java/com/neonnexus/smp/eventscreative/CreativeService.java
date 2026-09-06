package com.neonnexus.smp.eventscreative;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Atrium platform layer. PlotSquared owns plot data; this service stores only a verified reference.
 * Persistence/outbox calls belong on an asynchronous executor supplied by the platform host.
 */
public final class CreativeService {
    private final CreativeRepository creative;
    private final PlotSquaredAdapter plots;
    private final WorldEditAdapter worldEdit;
    private final OutboxRepository outbox;
    private final AuditLog audit;
    private final Clock clock;

    public CreativeService(CreativeRepository creative, PlotSquaredAdapter plots, WorldEditAdapter worldEdit,
                           OutboxRepository outbox, AuditLog audit, Clock clock) {
        this.creative = Objects.requireNonNull(creative, "creative");
        this.plots = Objects.requireNonNull(plots, "plots");
        this.worldEdit = Objects.requireNonNull(worldEdit, "worldEdit");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuildSubmission submit(UUID playerId, double x, double y, double z, String title, String description,
                                  List<String> imageReferences, UUID correlationId) {
        return creative.inTransaction(() -> {
            if (!plots.available()) throw new IllegalStateException("PlotSquared is unavailable; build submission is safely disabled");
            PlotSquaredAdapter.PlotReference plot = plots.plotAt(CanonicalWorlds.ATRIUM, x, y, z)
                .orElseThrow(() -> new IllegalArgumentException("You must stand in an Atrium plot to submit a build"));
            if (!CanonicalWorlds.ATRIUM.equals(plot.worldId())) throw new IllegalArgumentException("Build submissions are only accepted in The Atrium");
            if (!plots.isOwnerOrTrusted(playerId, plot)) throw new SecurityException("Only a plot owner or trusted builder can submit this build");
            BuildSubmission submission = new BuildSubmission(UUID.randomUUID(), playerId, CanonicalWorlds.ATRIUM, plot.plotId(), title,
                description, imageReferences, clock.instant(), SubmissionState.PENDING, null, null, null, 0);
            creative.saveSubmission(submission);
            audit(playerId, "BUILD_SUBMITTED", "creative_submission", submission.id().toString(), null, null, correlationId);
            publish(submission, PlatformEventType.BUILD_SUBMITTED, Map.of("submissionId", submission.id().toString(), "plotId", submission.plotId(), "title", submission.title(), "state", submission.state().name()));
            return submission;
        });
    }

    public BuildSubmission review(UUID staffId, UUID submissionId, String note, UUID correlationId) {
        return creative.inTransaction(() -> {
            BuildSubmission submission = requireSubmission(submissionId);
            SubmissionState before = submission.state();
            submission.review(staffId, note, clock.instant());
            creative.saveSubmission(submission);
            audit(staffId, "BUILD_REVIEWED", "creative_submission", submission.id().toString(), before.name(), submission.state().name(), correlationId);
            publish(submission, PlatformEventType.BUILD_REVIEWED, Map.of("submissionId", submission.id().toString(), "state", submission.state().name(), "reviewNote", string(note)));
            return submission;
        });
    }

    public BuildSubmission reject(UUID staffId, UUID submissionId, String reason, UUID correlationId) {
        return creative.inTransaction(() -> {
            BuildSubmission submission = requireSubmission(submissionId);
            SubmissionState before = submission.state();
            submission.reject(staffId, reason, clock.instant());
            creative.saveSubmission(submission);
            audit(staffId, "BUILD_REJECTED", "creative_submission", submission.id().toString(), before.name(), submission.state().name(), correlationId);
            publish(submission, PlatformEventType.BUILD_REJECTED, Map.of("submissionId", submission.id().toString(), "state", submission.state().name(), "reviewNote", reason));
            return submission;
        });
    }

    public FeaturedBuild feature(UUID staffId, UUID submissionId, String note, int showcaseOrder, UUID correlationId) {
        return creative.inTransaction(() -> {
            BuildSubmission submission = requireSubmission(submissionId);
            SubmissionState before = submission.state();
            submission.feature(staffId, note, clock.instant());
            FeaturedBuild featured = new FeaturedBuild(UUID.randomUUID(), submission.id(), submission.worldId(), submission.plotId(), clock.instant(), staffId, showcaseOrder);
            creative.saveSubmission(submission);
            creative.saveFeaturedBuild(featured);
            audit(staffId, "BUILD_FEATURED", "creative_submission", submission.id().toString(), before.name(), submission.state().name(), correlationId);
            publish(submission, PlatformEventType.BUILD_FEATURED, Map.of("submissionId", submission.id().toString(), "featuredBuildId", featured.id().toString(), "plotId", submission.plotId(), "title", submission.title(), "showcaseOrder", showcaseOrder));
            return featured;
        });
    }

    public BuildSubmission withdraw(UUID playerId, UUID submissionId, UUID correlationId) {
        return creative.inTransaction(() -> {
            BuildSubmission submission = requireSubmission(submissionId);
            if (!submission.submitterId().equals(playerId)) throw new SecurityException("Only the submitting player can withdraw a build");
            SubmissionState before = submission.state();
            submission.withdraw();
            creative.saveSubmission(submission);
            audit(playerId, "BUILD_WITHDRAWN", "creative_submission", submission.id().toString(), before.name(), submission.state().name(), correlationId);
            return submission;
        });
    }

    public boolean canUseWorldEdit(UUID playerId) { return worldEdit.available() && worldEdit.canUseWorldEdit(playerId, CanonicalWorlds.ATRIUM); }

    private BuildSubmission requireSubmission(UUID id) { return creative.findSubmission(id).orElseThrow(() -> new IllegalArgumentException("Build submission not found")); }
    private void audit(UUID actor, String action, String type, String target, String before, String after, UUID correlation) {
        audit.record(new AuditEntry(actor, action, type, target, CanonicalWorlds.ATRIUM, before, after, null, correlation, clock.instant()));
    }
    private void publish(BuildSubmission submission, PlatformEventType type, Map<String, Object> payload) {
        outbox.enqueue(new PlatformOutboxEvent(UUID.randomUUID(), "creative_submission", submission.id(), type,
            Set.of(PlatformDestination.WEBSITE, PlatformDestination.DISCORD), payload,
            type.name() + ":submission:" + submission.id() + ":r" + submission.revision(), 0, clock.instant()));
    }
    private static String string(String value) { return value == null ? "" : value; }
}
