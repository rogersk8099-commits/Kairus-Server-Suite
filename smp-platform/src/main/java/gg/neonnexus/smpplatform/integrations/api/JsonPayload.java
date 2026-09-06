package gg.neonnexus.smpplatform.integrations.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Minimal predictable JSON encoder for bounded platform event payloads; configuration secrets are never accepted. */
public final class JsonPayload {
    private JsonPayload() { }
    public static String event(UUID eventId, String type, Map<String, ?> body) {
        return object(Map.of("eventId", eventId.toString(), "type", type, "occurredAt", Instant.now().toString(), "body", body));
    }
    public static String object(Map<String, ?> map) {
        StringBuilder out = new StringBuilder("{"); boolean first = true;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (!first) out.append(','); first = false;
            out.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
        }
        return out.append('}').toString();
    }
    @SuppressWarnings("unchecked")
    private static String value(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof UUID || value instanceof Instant || value instanceof Enum<?>) return quote(value.toString());
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{"); boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(','); first = false;
                out.append(quote(Objects.toString(entry.getKey()))).append(':').append(value(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder out = new StringBuilder("["); boolean first = true;
            for (Object item : collection) { if (!first) out.append(','); first = false; out.append(value(item)); }
            return out.append(']').toString();
        }
        return quote(value.toString());
    }
    public static String quote(String raw) {
        StringBuilder out = new StringBuilder("\\\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\"); case '\"' -> out.append("\\\""); case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('\"').toString();
    }
}
