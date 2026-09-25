package gg.neonnexus.smpplatform.lifecycle.paper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Small, restart-safe interval clock for the resource world. The next reset is always the next
 * interval boundary, never an immediate destructive action on plugin/server restart.
 */
public final class IntervalQuarryResetScheduler {
    private final Duration interval;
    private final List<Duration> warnings;
    private Instant nextReset;
    private Instant lastPoll;
    private final Set<Duration> emittedWarnings = new HashSet<>();

    public IntervalQuarryResetScheduler(Duration interval, List<Duration> warnings, Instant now) {
        if (interval.isZero() || interval.isNegative()) throw new IllegalArgumentException("interval must be positive");
        this.interval = interval;
        this.warnings = warnings.stream().filter(value -> !value.isNegative() && value.compareTo(interval) < 0).sorted().toList();
        this.lastPoll = now;
        long seconds = interval.toSeconds();
        long next = ((now.getEpochSecond() / seconds) + 1) * seconds;
        this.nextReset = Instant.ofEpochSecond(next).truncatedTo(ChronoUnit.SECONDS);
    }

    public synchronized Poll poll(Instant now) {
        List<Duration> dueWarnings = new ArrayList<>();
        for (Duration warning : warnings) {
            Instant threshold = nextReset.minus(warning);
            if (!emittedWarnings.contains(warning) && !lastPoll.isAfter(threshold) && !now.isBefore(threshold)) {
                emittedWarnings.add(warning);
                dueWarnings.add(warning);
            }
        }
        boolean due = !now.isBefore(nextReset);
        if (due) {
            do { nextReset = nextReset.plus(interval); } while (!now.isBefore(nextReset));
            emittedWarnings.clear();
        }
        lastPoll = now;
        return new Poll(List.copyOf(dueWarnings), due, nextReset);
    }

    public synchronized Instant nextReset() { return nextReset; }
    public record Poll(List<Duration> warnings, boolean resetDue, Instant nextReset) { }
}
