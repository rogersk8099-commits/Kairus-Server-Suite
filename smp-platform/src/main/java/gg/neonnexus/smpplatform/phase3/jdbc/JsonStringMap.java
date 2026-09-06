package gg.neonnexus.smpplatform.phase3.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal JSON object codec for non-null String-to-String transaction metadata. */
final class JsonStringMap {
    private JsonStringMap() { }

    static String write(Map<String, String> values) {
        StringBuilder result = new StringBuilder("{");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (result.length() > 1) result.append(',');
            result.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return result.append('}').toString();
    }

    static Map<String, String> read(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return Map.of();
        Cursor cursor = new Cursor(json);
        cursor.expect('{');
        Map<String, String> values = new LinkedHashMap<>();
        if (cursor.consume('}')) return Map.of();
        do {
            String key = cursor.string(); cursor.expect(':'); String value = cursor.string(); values.put(key, value);
        } while (cursor.consume(','));
        cursor.expect('}'); cursor.finished();
        return Map.copyOf(values);
    }

    private static String escape(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '"' -> result.append("\\\""); case '\\' -> result.append("\\\\"); case '\b' -> result.append("\\b"); case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n"); case '\r' -> result.append("\\r"); case '\t' -> result.append("\\t");
                default -> { if (ch < 0x20) result.append(String.format("\\u%04x", (int) ch)); else result.append(ch); }
            }
        }
        return result.toString();
    }

    private static final class Cursor {
        private final String input; private int index;
        Cursor(String input) { this.input = input; }
        boolean consume(char token) { whitespace(); if (index < input.length() && input.charAt(index) == token) { index++; return true; } return false; }
        void expect(char token) { if (!consume(token)) throw new IllegalArgumentException("Malformed transaction metadata JSON"); }
        String string() {
            whitespace(); expect('"'); StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char ch = input.charAt(index++); if (ch == '"') return result.toString();
                if (ch != '\\') { result.append(ch); continue; }
                if (index >= input.length()) throw new IllegalArgumentException("Malformed transaction metadata JSON");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped); case 'b' -> result.append('\b'); case 'f' -> result.append('\f'); case 'n' -> result.append('\n'); case 'r' -> result.append('\r'); case 't' -> result.append('\t');
                    case 'u' -> { if (index + 4 > input.length()) throw new IllegalArgumentException("Malformed transaction metadata JSON"); try { result.append((char) Integer.parseInt(input.substring(index, index + 4), 16)); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Malformed transaction metadata JSON", exception); } index += 4; }
                    default -> throw new IllegalArgumentException("Malformed transaction metadata JSON");
                }
            }
            throw new IllegalArgumentException("Malformed transaction metadata JSON");
        }
        void finished() { whitespace(); if (index != input.length()) throw new IllegalArgumentException("Malformed transaction metadata JSON"); }
        private void whitespace() { while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++; }
    }
}
