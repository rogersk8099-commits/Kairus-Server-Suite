package gg.neonnexus.smpplatform.lifecycle.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** A weekly local-time schedule. Zone rules are evaluated on every calculation, including DST changes. */
public record ZonedWeeklySchedule(DayOfWeek day, LocalTime time, ZoneId zone) {
    public ZonedWeeklySchedule {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(zone, "zone");
    }

    /** Returns the first scheduled instant on or strictly after {@code instant}. */
    public Instant nextAtOrAfter(Instant instant) {
        ZonedDateTime local = instant.atZone(zone);
        int delta = Math.floorMod(day.getValue() - local.getDayOfWeek().getValue(), 7);
        LocalDate candidateDate = local.toLocalDate().plusDays(delta);
        ZonedDateTime candidate = ZonedDateTime.of(candidateDate, time, zone);
        if (candidate.toInstant().isBefore(instant)) {
            candidate = ZonedDateTime.of(candidateDate.plusWeeks(1), time, zone);
        }
        return candidate.toInstant();
    }

    /** Returns the first scheduled instant strictly after {@code instant}. */
    public Instant nextAfter(Instant instant) {
        return nextAtOrAfter(instant.plusNanos(1));
    }
}
