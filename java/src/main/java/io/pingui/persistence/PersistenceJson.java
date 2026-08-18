package io.pingui.persistence;

import java.util.ArrayList;
import java.util.List;

/** Minimal JSON helpers for typed persistence columns (P27-002). */
final class PersistenceJson {
    private PersistenceJson() {}

    static String quote(String value) {
        StringBuilder sb = new StringBuilder((value == null ? 0 : value.length()) + 8);
        sb.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '\\' -> sb.append("\\\\");
                    case '"' -> sb.append("\\\"");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(ch);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String stringArray(List<String> values) {
        List<String> items = values == null ? List.of() : values;
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(items.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    static List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new PersistenceException("Expected JSON string array");
        }
        if (trimmed.length() == 2) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int index = 1;
        while (index < trimmed.length()) {
            while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) {
                index++;
            }
            if (index < trimmed.length() && trimmed.charAt(index) == ']') {
                return List.copyOf(values);
            }
            if (index >= trimmed.length() || trimmed.charAt(index) != '"') {
                throw new PersistenceException("Expected string in JSON array");
            }
            ParsedString parsed = readString(trimmed, index);
            values.add(parsed.value());
            index = parsed.nextIndex();
            while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) {
                index++;
            }
            if (index < trimmed.length() && trimmed.charAt(index) == ',') {
                index++;
                continue;
            }
            if (index < trimmed.length() && trimmed.charAt(index) == ']') {
                return List.copyOf(values);
            }
            throw new PersistenceException("Malformed JSON string array");
        }
        throw new PersistenceException("Unterminated JSON string array");
    }

    private static ParsedString readString(String json, int quoteIndex) {
        StringBuilder sb = new StringBuilder();
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char ch = json.charAt(index++);
            if (ch == '"') {
                return new ParsedString(sb.toString(), index);
            }
            if (ch == '\\') {
                if (index >= json.length()) {
                    throw new PersistenceException("Unterminated escape");
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> sb.append(escaped);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> throw new PersistenceException("Unsupported escape: \\" + escaped);
                }
                continue;
            }
            sb.append(ch);
        }
        throw new PersistenceException("Unterminated string");
    }

    private record ParsedString(String value, int nextIndex) {}
}
