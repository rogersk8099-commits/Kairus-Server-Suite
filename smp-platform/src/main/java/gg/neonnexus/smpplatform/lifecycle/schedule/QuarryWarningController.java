package gg.neonnexus.smpplatform.lifecycle.schedule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Delivers each configured Quarry countdown warning once and returns a reset due signal. */
public final class QuarryWarningController {
    private final ZonedWeeklySchedule schedule;
    private final List<Long> warningMinutes;
    private Instant checkedThrough;
    public QuarryWarningController(ZonedWeeklySchedule schedule, Collection<Long> warningMinutes, Instant initialCheckedThrough) {
        this.schedule = schedule;
        this.warningMinutes = warningMinutes.stream().distinct().sorted(Comparator.reverseOrder()).toList();
        this.checkedThrough = initialCheckedThrough;
    }
    public synchronized PollResult advance(Instant now) {
        if (now.isBefore(checkedThrough)) return new PollResult(List.of(), false, null);
        Instant reset = schedule.nextAfter(checkedThrough);
        List<Long> due = new ArrayList<>();
        for (long minutes : warningMinutes) {
            Instant warningAt = reset.minusSeconds(minutes * 60);
            if (!warningAt.isBefore(checkedThrough) && !warningAt.isAfter(now)) due.add(minutes);
        }
        boolean resetDue = !reset.isAfter(now);
        checkedThrough = now;
        return new PollResult(List.copyOf(due), resetDue, reset);
    }
    public record PollResult(List<Long> warningMinutes, boolean resetDue, Instant resetAt) { }
}
