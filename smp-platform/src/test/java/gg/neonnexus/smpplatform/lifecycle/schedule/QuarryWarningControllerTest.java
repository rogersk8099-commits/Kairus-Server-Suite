package gg.neonnexus.smpplatform.lifecycle.schedule;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuarryWarningControllerTest {
    @Test void mondayFourUtcProducesTwentyFourHourWarningAndResetDue() {
        ZonedWeeklySchedule schedule = new ZonedWeeklySchedule(DayOfWeek.MONDAY, LocalTime.of(4, 0), ZoneOffset.UTC);
        QuarryWarningController controller = new QuarryWarningController(schedule, List.of(1440L, 60L, 15L, 5L, 1L), Instant.parse("2025-01-05T03:59:00Z"));
        QuarryWarningController.PollResult warning = controller.advance(Instant.parse("2025-01-05T04:00:00Z"));
        assertEquals(List.of(1440L), warning.warningMinutes()); assertFalse(warning.resetDue());
        QuarryWarningController.PollResult reset = controller.advance(Instant.parse("2025-01-06T04:00:00Z"));
        assertTrue(reset.resetDue()); assertEquals(Instant.parse("2025-01-06T04:00:00Z"), reset.resetAt());
    }
}
