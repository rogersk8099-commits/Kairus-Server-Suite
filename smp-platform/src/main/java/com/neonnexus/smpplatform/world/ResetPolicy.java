package com.neonnexus.smpplatform.world;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

public sealed interface ResetPolicy permits ResetPolicy.None, ResetPolicy.Weekly, ResetPolicy.Manual {
    String kind();

    record None() implements ResetPolicy {
        @Override public String kind() { return "none"; }
    }

    record Weekly(DayOfWeek day, LocalTime time, ZoneId timezone, boolean safetyBackupRequired) implements ResetPolicy {
        public Weekly {
            Objects.requireNonNull(day, "day");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(timezone, "timezone");
        }
        @Override public String kind() { return "weekly"; }
    }

    record Manual(String reason) implements ResetPolicy {
        public Manual {
            reason = Objects.requireNonNullElse(reason, "manual").trim();
        }
        @Override public String kind() { return "manual"; }
    }
}
