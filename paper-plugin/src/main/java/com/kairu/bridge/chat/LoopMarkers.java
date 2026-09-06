package com.kairu.bridge.chat;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Adds an invisible provenance marker to Discord-originated Minecraft broadcasts. A bridge listener
 * must inspect the raw message before sanitizing it; sanitization removes format characters.
 */
public final class LoopMarkers {
    /* Three invisible format characters make the sentinel non-rendering in vanilla chat clients. */
    private static final String MARKER_TEXT = "\u2063\u2060\u2063";
    private static final Pattern MARKER = Pattern.compile(MARKER_TEXT, Pattern.LITERAL);

    private LoopMarkers() { }

    public static String markDiscordOrigin(String visibleText, String messageId) {
        Objects.requireNonNull(visibleText, "visibleText");
        if (!isSafeMessageId(messageId)) throw new IllegalArgumentException("Unsafe queue message id");
        return visibleText + MARKER_TEXT;
    }

    public static boolean containsBridgeMarker(String text) { return text != null && MARKER.matcher(text).find(); }
    public static String removeBridgeMarkers(String text) { return text == null ? "" : MARKER.matcher(text).replaceAll(""); }
    public static boolean isSafeMessageId(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,128}"); }
}
