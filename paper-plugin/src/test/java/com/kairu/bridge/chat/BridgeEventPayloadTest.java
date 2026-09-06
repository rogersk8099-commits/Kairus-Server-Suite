package com.kairu.bridge.chat;

import com.kairu.bridge.payload.BridgeEventPayload;
import com.kairu.bridge.payload.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeEventPayloadTest {
    @Test
    void boundsSerializedPayloadByUtf8BytesAndPreservesContractIdentity() {
        BridgeEventPayload payload = new BridgeEventPayload(
                "event_123", BridgeEventType.CHAT, Instant.parse("2026-01-01T00:00:00Z"), "world",
                UUID.fromString("6b3a5b8f-0dda-4dcc-8a48-579ef5b2c78a"), "Steve",
                "😀".repeat(200), Map.of("source", "minecraft"));

        String json = payload.toJson(512);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= 512);
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(json);
        assertEquals("event_123", parsed.get("eventId"));
        assertEquals("CHAT", parsed.get("eventType"));
        assertTrue(((String) parsed.get("content")).codePointCount(0, ((String) parsed.get("content")).length()) < 200);
    }
}
