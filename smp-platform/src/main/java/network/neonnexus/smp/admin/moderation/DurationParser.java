package network.neonnexus.smp.admin.moderation;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PATTERN = Pattern.compile("^(\\d{1,6})([smhdw])$");
    private DurationParser() { }
    public static Optional<Duration> parse(String input) {
        Matcher matcher = PATTERN.matcher(input == null ? "" : input.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return Optional.empty();
        long number = Long.parseLong(matcher.group(1));
        return Optional.of(switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(number); case "m" -> Duration.ofMinutes(number); case "h" -> Duration.ofHours(number);
            case "d" -> Duration.ofDays(number); case "w" -> Duration.ofDays(Math.multiplyExact(number, 7)); default -> throw new IllegalStateException();
        });
    }
}
