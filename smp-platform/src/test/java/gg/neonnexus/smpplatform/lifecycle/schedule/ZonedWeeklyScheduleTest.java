package gg.neonnexus.smpplatform.lifecycle.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.*;
import org.junit.jupiter.api.Test;

class ZonedWeeklyScheduleTest {
    @Test void londonScheduleUsesSummerOffsetAfterDstSpringTransition() {
        ZonedWeeklySchedule schedule = new ZonedWeeklySchedule(DayOfWeek.SUNDAY, LocalTime.of(22, 0), ZoneId.of("Europe/London"));
        Instant next = schedule.nextAtOrAfter(Instant.parse("2025-03-29T12:00:00Z"));
        assertEquals(Instant.parse("2025-03-30T21:00:00Z"), next); // 22:00 BST
    }
    @Test void londonScheduleUsesWinterOffsetAfterDstAutumnTransition() {
        ZonedWeeklySchedule schedule = new ZonedWeeklySchedule(DayOfWeek.SUNDAY, LocalTime.of(22, 0), ZoneId.of("Europe/London"));
        Instant next = schedule.nextAtOrAfter(Instant.parse("2025-10-25T12:00:00Z"));
        assertEquals(Instant.parse("2025-10-26T22:00:00Z"), next); // 22:00 GMT
    }
}
