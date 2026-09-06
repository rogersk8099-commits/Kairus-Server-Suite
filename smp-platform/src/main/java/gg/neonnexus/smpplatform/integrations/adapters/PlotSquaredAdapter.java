package gg.neonnexus.smpplatform.integrations.adapters;

import java.util.Objects;
import java.util.UUID;

/** Plot mechanics remain PlotSquared's responsibility; this adapter only exposes platform submissions and metadata. */
public final class PlotSquaredAdapter extends SoftAdapter implements DelegatingAdapters.PlotSquared {
    @FunctionalInterface public interface CurrentPlotLookup { String currentPlot(UUID playerId); }
    @FunctionalInterface public interface Submission { boolean submit(UUID playerId, String title, String description); }
    private final CurrentPlotLookup lookup; private final Submission submission;
    public PlotSquaredAdapter(SoftDependency dependency, CurrentPlotLookup lookup, Submission submission) { super(dependency); this.lookup = Objects.requireNonNull(lookup); this.submission = Objects.requireNonNull(submission); }
    @Override public AdapterResult<String> currentPlot(UUID playerId) { return available() ? AdapterResult.of(lookup.currentPlot(playerId)) : unavailable(); }
    @Override public AdapterResult<Boolean> submitCurrentPlot(UUID playerId, String title, String description) { return available() ? AdapterResult.of(submission.submit(playerId, title, description)) : unavailable(); }
}
