package com.neonnexus.smpplatform.world;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

public sealed interface ResetPolicy permits ResetPolicy.None, ResetPolicy.Weekly, ResetPolicy.Interval, ResetPolicy.Manual {
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

    record Interval(Duration interval, boolean safetyBackupRequired) implements ResetPolicy {
        public Interval {
            interval = Objects.requireNonNull(interval, "interval");
            if (interval.isZero() || interval.isNegative()) throw new IllegalArgumentException("interval must be positive");
        }
        @Override public String kind() { return "interval"; }
    }

    record Manual(String reason) implements ResetPolicy {
        public Manual {
            reason = Objects.requireNonNullElse(reason, "manual").trim();
        }
        @Override public String kind() { return "manual"; }
    }
}
