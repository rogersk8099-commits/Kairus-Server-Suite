package com.kairu.bridge.payload;

import java.util.*;

/** Small JSON codec used to avoid a runtime dependency inside the Paper plugin. */
public final class Json {
    private Json() { }

    public static String quote(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }

    public static String object(Map<String, ?> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
        }
        return out.append('}').toString();
    }

    public static String array(Collection<?> values) {
        StringBuilder out = new StringBuilder("["); boolean first = true;
        for (Object value : values) { if (!first) out.append(','); first = false; out.append(value(value)); }
        return out.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    private static String value(Object input) {
        if (input == null) return "null";
        if (input instanceof String || input instanceof UUID || input instanceof Enum<?>) return quote(String.valueOf(input));
        if (input instanceof Number || input instanceof Boolean) return String.valueOf(input);
        if (input instanceof Map<?, ?> map) return object((Map<String, ?>) map);
        if (input instanceof Collection<?> collection) return array(collection);
        return quote(String.valueOf(input));
    }

    public static Object parse(String source) {
        if (source == null || source.length() > 1_000_000) throw new IllegalArgumentException("JSON response is invalid or too large");
        Parser parser = new Parser(source); Object result = parser.value(); parser.space();
        if (parser.position != source.length()) throw new IllegalArgumentException("Trailing JSON content");
        return result;
    }

    private static final class Parser {
        private final String s; private int position;
        Parser(String s) { this.s = s; }
        void space() { while (position < s.length() && Character.isWhitespace(s.charAt(position))) position++; }
        Object value() {
            space(); if (position >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return switch (s.charAt(position)) {
                case '{' -> object(); case '[' -> array(); case '"' -> string(); case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE); case 'n' -> literal("null", null); default -> number();
            };
        }
        Object literal(String token, Object result) { if (!s.startsWith(token, position)) throw new IllegalArgumentException("Invalid JSON token"); position += token.length(); return result; }
        Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>(); expect('{'); space(); if (eat('}')) return out;
            do { space(); String key = string(); space(); expect(':'); out.put(key, value()); space(); } while (eat(',')); expect('}'); return out;
        }
        List<Object> array() {
            List<Object> out = new ArrayList<>(); expect('['); space(); if (eat(']')) return out;
            do { out.add(value()); space(); } while (eat(',')); expect(']'); return out;
        }
        String string() {
            expect('"'); StringBuilder out = new StringBuilder();
            while (position < s.length()) { char c = s.charAt(position++); if (c == '"') return out.toString(); if (c == '\\') {
                if (position >= s.length()) throw new IllegalArgumentException("Invalid escape"); char e = s.charAt(position++);
                switch (e) { case '"' -> out.append('"'); case '\\' -> out.append('\\'); case '/' -> out.append('/'); case 'b' -> out.append('\b'); case 'f' -> out.append('\f'); case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t'); case 'u' -> { if (position + 4 > s.length()) throw new IllegalArgumentException("Invalid unicode escape"); out.append((char) Integer.parseInt(s.substring(position, position + 4), 16)); position += 4; } default -> throw new IllegalArgumentException("Invalid escape"); }
            } else { if (c < 0x20) throw new IllegalArgumentException("Invalid control character"); out.append(c); } }
            throw new IllegalArgumentException("Unterminated string");
        }
        Number number() { int start = position; if (eat('-')) { } while (position < s.length() && Character.isDigit(s.charAt(position))) position++; if (eat('.')) while (position < s.length() && Character.isDigit(s.charAt(position))) position++; if (position < s.length() && (s.charAt(position) == 'e' || s.charAt(position) == 'E')) { position++; if (position < s.length() && (s.charAt(position) == '+' || s.charAt(position) == '-')) position++; while (position < s.length() && Character.isDigit(s.charAt(position))) position++; } try { return Double.valueOf(s.substring(start, position)); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number"); } }
        boolean eat(char c) { if (position < s.length() && s.charAt(position) == c) { position++; return true; } return false; }
        void expect(char c) { if (!eat(c)) throw new IllegalArgumentException("Expected '" + c + "'"); }
    }
}
