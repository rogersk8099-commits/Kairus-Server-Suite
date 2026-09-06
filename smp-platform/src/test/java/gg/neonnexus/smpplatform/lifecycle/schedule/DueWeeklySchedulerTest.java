package gg.neonnexus.smpplatform.lifecycle.schedule;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class DueWeeklySchedulerTest {
    @Test void aWeeklyInstantIsReportedOnceAcrossSuccessivePolls() {
        ZonedWeeklySchedule schedule = new ZonedWeeklySchedule(DayOfWeek.SUNDAY, LocalTime.of(22, 0), ZoneOffset.UTC);
        DueWeeklyScheduler due = new DueWeeklyScheduler(schedule, Instant.parse("2025-01-05T21:59:00Z"));
        assertEquals(Instant.parse("2025-01-05T22:00:00Z"), due.advance(Instant.parse("2025-01-05T22:00:00Z")).orElseThrow());
        assertTrue(due.advance(Instant.parse("2025-01-05T22:00:30Z")).isEmpty());
    }
}
