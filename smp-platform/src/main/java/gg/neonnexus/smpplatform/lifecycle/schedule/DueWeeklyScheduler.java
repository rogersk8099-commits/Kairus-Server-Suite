package gg.neonnexus.smpplatform.lifecycle.schedule;

import java.time.Instant;
import java.util.Optional;

/** Detects a weekly scheduled instant crossed between two resilient polling passes. */
public final class DueWeeklyScheduler {
    private final ZonedWeeklySchedule schedule;
    private Instant checkedThrough;
    public DueWeeklyScheduler(ZonedWeeklySchedule schedule, Instant initialCheckedThrough) {
        this.schedule = schedule; this.checkedThrough = initialCheckedThrough;
    }
    public synchronized Optional<Instant> advance(Instant now) {
        if (now.isBefore(checkedThrough)) return Optional.empty();
        Instant candidate = schedule.nextAfter(checkedThrough);
        checkedThrough = now;
        return candidate.isAfter(now) ? Optional.empty() : Optional.of(candidate);
    }
}
