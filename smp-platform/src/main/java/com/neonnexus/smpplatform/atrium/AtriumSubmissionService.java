package com.neonnexus.smpplatform.atrium;

import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import org.bukkit.entity.Player;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable Atrium submissions. PlotSquared is queried before any database mutation. */
public final class AtriumSubmissionService {
    public record Submission(UUID id, String plotId, String state, String title) { }
    public record PendingSubmission(UUID id, String title, String plotId, UUID submitterId, Instant submittedAt) { }
    public record FeaturedBuild(UUID submissionId, String title, String plotId, Instant featuredAt) { }
    private final DataSource dataSource;
    private final String atriumWorldName;

    public AtriumSubmissionService(DataSource dataSource, String atriumWorldName) {
        this.dataSource = dataSource; this.atriumWorldName = atriumWorldName;
    }

    /** Must be called on Paper's primary thread so the PlotSquared player state is current. */
    public SubmissionContext verifyCurrentPlot(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(atriumWorldName)) throw new IllegalArgumentException("Build submissions are available only in The Atrium.");
        PlotPlayer<?> plotPlayer = new PlotAPI().wrapPlayer(player.getUniqueId());
        Plot plot = plotPlayer == null ? null : plotPlayer.getCurrentPlot();
        if (plot == null || !plot.hasOwner()) throw new IllegalArgumentException("Stand inside a claimed Atrium plot to submit a build.");
        if (!plot.isOwner(player.getUniqueId()) && !plot.isAdded(player.getUniqueId())) throw new IllegalArgumentException("Only the plot owner or a trusted builder can submit this build.");
        return new SubmissionContext(player.getUniqueId(), plot.getId().toString());
    }

    public Submission submit(SubmissionContext context, String title, String description) {
        String safeTitle = required(title, "title", 160); String safeDescription = required(description, "description", 2000);
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                try (PreparedStatement submission = connection.prepareStatement("INSERT INTO smp_creative_submissions (id,submitter_id,world_id,plot_id,title,description,image_references,state,submitted_at,revision) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)")) {
                    submission.setObject(1, id); submission.setObject(2, context.playerId()); submission.setString(3, "atrium"); submission.setString(4, context.plotId()); submission.setString(5, safeTitle); submission.setString(6, safeDescription); submission.setString(7, "[]"); submission.setString(8, "SUBMITTED"); submission.setTimestamp(9, Timestamp.from(now)); submission.setLong(10, 0); submission.executeUpdate();
                }
                audit(connection, context.playerId(), "BUILD_SUBMITTED", id.toString(), Map.of("plotId", context.plotId(), "title", safeTitle), now);
                connection.commit(); return new Submission(id, context.plotId(), "SUBMITTED", safeTitle);
            } catch (Exception exception) {
                connection.rollback(); throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (Exception exception) { throw new IllegalStateException("Build submission could not be saved; no partial record was kept.", exception); }
    }

    public Submission review(UUID staffId, UUID submissionId, String requestedState, String note) {
        String state = switch (requestedState.toUpperCase(java.util.Locale.ROOT)) { case "UNDER_REVIEW", "FEATURED", "REJECTED", "ARCHIVED" -> requestedState.toUpperCase(java.util.Locale.ROOT); default -> throw new IllegalArgumentException("Review state must be under_review, featured, rejected, or archived."); };
        String safeNote = required(note, "review note", 2000); Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                String title; String plotId;
                try (PreparedStatement select = connection.prepareStatement("SELECT title,plot_id,state FROM smp_creative_submissions WHERE id=? FOR UPDATE")) {
                    select.setObject(1, submissionId); var result = select.executeQuery(); if (!result.next()) throw new IllegalArgumentException("Build submission was not found."); title = result.getString(1);
                    plotId = result.getString(2); String current = result.getString(3);
                    if (current.equals("FEATURED") || current.equals("REJECTED") || current.equals("ARCHIVED")) throw new IllegalArgumentException("That build submission is already finalised.");
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE smp_creative_submissions SET state=?,reviewer_id=?,review_note=?,reviewed_at=?,revision=revision+1 WHERE id=?")) {
                    update.setString(1, state); update.setObject(2, staffId); update.setString(3, safeNote); update.setTimestamp(4, Timestamp.from(now)); update.setObject(5, submissionId); update.executeUpdate();
                }
                if (state.equals("FEATURED")) {
                    try (PreparedStatement featured = connection.prepareStatement("INSERT INTO smp_featured_builds (id,submission_id,world_id,plot_id,featured_at,featured_by,showcase_order) VALUES (?,?,?,?,?,?,?) ON CONFLICT (submission_id) DO NOTHING")) {
                        featured.setObject(1, UUID.randomUUID()); featured.setObject(2, submissionId); featured.setString(3, "atrium"); featured.setString(4, plotId); featured.setTimestamp(5, Timestamp.from(now)); featured.setObject(6, staffId); featured.setInt(7, 0); featured.executeUpdate();
                    }
                }
                audit(connection, staffId, "BUILD_" + state, submissionId.toString(), Map.of("note", safeNote), now);
                connection.commit(); return new Submission(submissionId, "", state, title);
            } catch (Exception exception) {
                connection.rollback(); throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Build review could not be saved; no partial review was kept.", exception); }
    }

    /** Database-only query for the staff GUI. It deliberately returns pending work only. */
    public List<PendingSubmission> pending(int limit) {
        int safeLimit = Math.max(1, Math.min(45, limit)); List<PendingSubmission> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id,title,plot_id,submitter_id,submitted_at FROM smp_creative_submissions WHERE state IN ('SUBMITTED','UNDER_REVIEW') ORDER BY submitted_at ASC LIMIT ?")) {
            statement.setInt(1, safeLimit); var result = statement.executeQuery();
            while (result.next()) rows.add(new PendingSubmission(result.getObject(1, UUID.class), result.getString(2), result.getString(3), result.getObject(4, UUID.class), result.getTimestamp(5).toInstant()));
            return List.copyOf(rows);
        } catch (Exception exception) { throw new IllegalStateException("Atrium review queue could not be loaded.", exception); }
    }

    /** Player-facing showcase query. Archived showcase entries are intentionally excluded. */
    public List<FeaturedBuild> featured(int limit) {
        int safeLimit = Math.max(1, Math.min(45, limit)); List<FeaturedBuild> rows = new ArrayList<>();
        String sql = "SELECT s.id,s.title,s.plot_id,f.featured_at FROM smp_featured_builds f JOIN smp_creative_submissions s ON s.id=f.submission_id WHERE f.archived_at IS NULL ORDER BY f.showcase_order ASC,f.featured_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit); var result = statement.executeQuery();
            while (result.next()) rows.add(new FeaturedBuild(result.getObject(1, UUID.class), result.getString(2), result.getString(3), result.getTimestamp(4).toInstant()));
            return List.copyOf(rows);
        } catch (Exception exception) { throw new IllegalStateException("Featured Atrium builds could not be loaded.", exception); }
    }

    public UUID submitterId(UUID submissionId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT submitter_id FROM smp_creative_submissions WHERE id=?")) {
            statement.setObject(1, submissionId); var result = statement.executeQuery();
            if (!result.next()) throw new IllegalArgumentException("Build submission was not found.");
            return result.getObject(1, UUID.class);
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Build submission could not be loaded.", exception); }
    }

    private static void audit(Connection connection, UUID actor, String action, String target, Map<String, String> metadata, Instant now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO smp_audit_logs (id,actor_id,action,target_type,target_id,world_id,metadata,created_at) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?)")) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, actor); statement.setString(3, action); statement.setString(4, "creative_submission"); statement.setString(5, target); statement.setString(6, "atrium"); statement.setString(7, new com.google.gson.Gson().toJson(metadata)); statement.setTimestamp(8, Timestamp.from(now)); statement.executeUpdate();
        }
    }
    private static String required(String value, String label, int limit) { String normalized = value == null ? "" : value.trim(); if (normalized.isEmpty() || normalized.length() > limit) throw new IllegalArgumentException(label + " must be 1-" + limit + " characters."); return normalized; }
    public record SubmissionContext(UUID playerId, String plotId) { }
}
