package gg.neonnexus.smpplatform.lifecycle.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/** Computes warning instants relative to a reset instant without assuming a fixed local offset. */
public final class ResetWarningSchedule {
    private ResetWarningSchedule() { }

    public static List<Instant> warningInstants(Instant resetAt, Collection<Long> minutesBefore) {
        return minutesBefore.stream().distinct().sorted(java.util.Comparator.reverseOrder())
                .map(minutes -> resetAt.minus(minutes, ChronoUnit.MINUTES)).toList();
    }
}
