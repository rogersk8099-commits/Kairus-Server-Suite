package gg.neonnexus.smpplatform.phase3.guild;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class GuildText {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _-]{2,31}");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9]{2,8}");

    private GuildText() { }

    static String requireName(String input) {
        String value = Objects.requireNonNull(input, "name").trim();
        if (!NAME.matcher(value).matches()) throw new IllegalArgumentException("guild name must be 3-32 safe characters");
        return value;
    }

    static String requireTag(String input) {
        String value = Objects.requireNonNull(input, "tag").trim().toUpperCase(Locale.ROOT);
        if (!TAG.matcher(value).matches()) throw new IllegalArgumentException("guild tag must be 2-8 alphanumeric characters");
        return value;
    }

    static String normalizeDescription(String input) {
        String value = input == null ? "" : input.trim();
        if (value.length() > 512) throw new IllegalArgumentException("guild description exceeds 512 characters");
        return value;
    }
}
