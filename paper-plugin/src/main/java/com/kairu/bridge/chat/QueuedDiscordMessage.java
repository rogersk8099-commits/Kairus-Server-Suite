package com.kairu.bridge.chat;

import java.util.Map;

/** Untrusted message envelope received from the authenticated central control-plane queue. */
public record QueuedDiscordMessage(String id, String content, String displayName, String targetWorld) {
    public QueuedDiscordMessage {
        if (!LoopMarkers.isSafeMessageId(id)) throw new IllegalArgumentException("Unsafe queued chat id");
        content = content == null ? "" : content;
        displayName = displayName == null ? "Discord" : displayName;
        targetWorld = targetWorld == null ? "" : targetWorld;
    }

    public static QueuedDiscordMessage fromMap(Map<?, ?> value) {
        if (value == null) throw new IllegalArgumentException("Queued chat entry is not an object");
        String id = text(value.get("id"), 128);
        String content = text(value.get("content"), 2_000);
        String targetWorld = text(value.get("targetWorld"), 128);
        Object author = value.get("author");
        String displayName = author instanceof Map<?, ?> map ? text(map.get("displayName"), 256) : text(value.get("displayName"), 256);
        if (id == null || content == null) throw new IllegalArgumentException("Queued chat entry is malformed");
        return new QueuedDiscordMessage(id, content, displayName == null ? "Discord" : displayName, targetWorld == null ? "" : targetWorld);
    }

    private static String text(Object value, int maximumCharacters) {
        return value instanceof String text && text.codePointCount(0, text.length()) <= maximumCharacters ? text : null;
    }
}
