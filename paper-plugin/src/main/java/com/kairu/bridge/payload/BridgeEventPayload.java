package com.kairu.bridge.payload;

import com.kairu.bridge.chat.BridgeEventType;
import com.kairu.bridge.chat.ChatSanitizer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A bounded, versioned-by-field-name envelope for a Minecraft-originated bridge event. */
public record BridgeEventPayload(
        String eventId,
        BridgeEventType eventType,
        Instant occurredAt,
        String worldName,
        UUID minecraftUuid,
        String minecraftName,
        String content,
        Map<String, String> details
) {
    private static final int MAXIMUM_DETAIL_ENTRIES = 8;

    public BridgeEventPayload {
        if (eventId == null || !eventId.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Unsafe event id");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        worldName = bounded(worldName, 128);
        minecraftName = bounded(minecraftName, 16);
        content = bounded(content, 500);
        details = cleanDetails(details);
    }

    public static BridgeEventPayload event(BridgeEventType type, String worldName, UUID minecraftUuid, String minecraftName,
                                            String content, Map<String, String> details) {
        return new BridgeEventPayload(UUID.randomUUID().toString(), type, Instant.now(), worldName, minecraftUuid, minecraftName, content, details);
    }

    /**
     * Renders a JSON envelope within a byte ceiling. Content is the only lossy field because event
     * identity, type, actor, and metadata are needed by the central API to process the event safely.
     */
    public String toJson(int maximumPayloadBytes) {
        if (maximumPayloadBytes < 1) throw new IllegalArgumentException("maximumPayloadBytes must be positive");
        String full = jsonFor(content);
        if (utf8Length(full) <= maximumPayloadBytes) return full;
        String empty = jsonFor("");
        if (utf8Length(empty) > maximumPayloadBytes) throw new IllegalArgumentException("Payload metadata exceeds configured byte limit");
        int low = 0;
        int high = content.codePointCount(0, content.length());
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String candidate = ChatSanitizer.truncateCodePoints(content, middle);
            if (utf8Length(jsonFor(candidate)) <= maximumPayloadBytes) low = middle;
            else high = middle - 1;
        }
        return jsonFor(ChatSanitizer.truncateCodePoints(content, low));
    }

    private String jsonFor(String renderedContent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", eventType.name());
        body.put("occurredAt", occurredAt.toString());
        if (!worldName.isBlank()) body.put("worldName", worldName);
        if (minecraftUuid != null) body.put("minecraftUuid", minecraftUuid.toString());
        if (!minecraftName.isBlank()) body.put("minecraftName", minecraftName);
        body.put("content", renderedContent);
        if (!details.isEmpty()) body.put("details", details);
        return Json.object(body);
    }

    private static Map<String, String> cleanDetails(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, String> output = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (output.size() >= MAXIMUM_DETAIL_ENTRIES) break;
            String key = entry.getKey();
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_-]{0,47}")) continue;
            String value = bounded(entry.getValue(), 160);
            if (!value.isBlank()) output.put(key, value);
        }
        return Map.copyOf(output);
    }

    private static String bounded(String value, int maximumCodePoints) { return ChatSanitizer.sanitize(value, maximumCodePoints); }
    private static int utf8Length(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
}
