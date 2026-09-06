package com.kairu.bridge.chat;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/** Converts user-controlled text to one visible, single-line, bounded representation. */
public final class ChatSanitizer {
    private ChatSanitizer() { }

    public static String sanitize(String input, int maximumCodePoints) {
        if (maximumCodePoints < 1) throw new IllegalArgumentException("maximumCodePoints must be positive");
        if (input == null || input.isBlank()) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        StringBuilder output = new StringBuilder(Math.min(normalized.length(), maximumCodePoints));
        boolean previousWhitespace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\u00a7' && offset < normalized.length()) {
                int formatCode = normalized.codePointAt(offset);
                if (isLegacyFormatCode(formatCode)) {
                    offset += Character.charCount(formatCode);
                    continue;
                }
            }
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || type == Character.FORMAT || type == Character.PRIVATE_USE) continue;
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (output.length() > 0 && !previousWhitespace) output.append(' ');
                previousWhitespace = true;
                continue;
            }
            output.appendCodePoint(codePoint);
            previousWhitespace = false;
        }
        return truncateCodePoints(output.toString().strip(), maximumCodePoints);
    }

    public static String truncateCodePoints(String input, int maximumCodePoints) {
        if (input == null || input.isEmpty()) return "";
        if (maximumCodePoints < 0) throw new IllegalArgumentException("maximumCodePoints must not be negative");
        int end = input.offsetByCodePoints(0, Math.min(maximumCodePoints, input.codePointCount(0, input.length())));
        return input.substring(0, end);
    }

    /** Never splits a Unicode surrogate pair while satisfying a UTF-8 byte ceiling. */
    public static String truncateUtf8(String input, int maximumBytes) {
        if (input == null || input.isEmpty() || maximumBytes == 0) return "";
        if (maximumBytes < 0) throw new IllegalArgumentException("maximumBytes must not be negative");
        int used = 0;
        int end = 0;
        while (end < input.length()) {
            int codePoint = input.codePointAt(end);
            int bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (used + bytes > maximumBytes) break;
            used += bytes;
            end += Character.charCount(codePoint);
        }
        return input.substring(0, end);
    }

    public static int utf8Length(String input) { return input == null ? 0 : input.getBytes(StandardCharsets.UTF_8).length; }

    private static boolean isLegacyFormatCode(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9') || (codePoint >= 'a' && codePoint <= 'f')
                || (codePoint >= 'A' && codePoint <= 'F') || "kKlLmMnNoOrRxX".indexOf(codePoint) >= 0;
    }
}
