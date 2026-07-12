package io.pingui.telemetry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Shared JSON helpers for telemetry DTOs (no Jackson). */
final class TelemetryJson {
    private TelemetryJson() {}

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    static String stringArray(List<String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(values.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    static String labelsObject(Map<String, String> labels) {
        Map<String, String> ordered = new TreeMap<>(labels);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    static Map<String, String> copyLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(labels).entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("label keys must be non-blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("label values must be non-null");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return value;
    }

    static String readStringField(String json, String field) {
        String value = readOptionalStringField(json, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return value;
    }

    static String readOptionalStringField(String json, String field) {
        int valueIndex = indexAfterKey(json, field);
        if (valueIndex < 0) {
            return null;
        }
        int index = skipWs(json, valueIndex);
        if (index >= json.length()) {
            return null;
        }
        if (json.startsWith("null", index)) {
            return null;
        }
        if (json.charAt(index) != '"') {
            throw new IllegalArgumentException("Expected string for " + field);
        }
        return readJsonString(json, index);
    }

    static Double readOptionalNumberField(String json, String field) {
        int valueIndex = indexAfterKey(json, field);
        if (valueIndex < 0) {
            return null;
        }
        int index = skipWs(json, valueIndex);
        if (index >= json.length() || json.startsWith("null", index)) {
            return null;
        }
        int end = index;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                end++;
            } else {
                break;
            }
        }
        if (end == index) {
            throw new IllegalArgumentException("Expected number for " + field);
        }
        try {
            return Double.parseDouble(json.substring(index, end));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number for " + field, ex);
        }
    }

    static List<String> readOptionalStringArray(String json, String field) {
        int valueIndex = indexAfterKey(json, field);
        if (valueIndex < 0) {
            return null;
        }
        int index = skipWs(json, valueIndex);
        if (index >= json.length() || json.startsWith("null", index)) {
            return null;
        }
        if (json.charAt(index) != '[') {
            throw new IllegalArgumentException("Expected array for " + field);
        }
        index++;
        List<String> values = new ArrayList<>();
        while (index < json.length()) {
            index = skipWs(json, index);
            if (index < json.length() && json.charAt(index) == ']') {
                return values;
            }
            if (index >= json.length() || json.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected string in " + field);
            }
            String value = readJsonString(json, index);
            values.add(value);
            index = skipAfterString(json, index);
            index = skipWs(json, index);
            if (index < json.length() && json.charAt(index) == ',') {
                index++;
                continue;
            }
            if (index < json.length() && json.charAt(index) == ']') {
                return values;
            }
            throw new IllegalArgumentException("Malformed array for " + field);
        }
        throw new IllegalArgumentException("Unterminated array for " + field);
    }

    static Map<String, String> readLabelsObject(String json, String field) {
        int valueIndex = indexAfterKey(json, field);
        if (valueIndex < 0) {
            return Map.of();
        }
        int index = skipWs(json, valueIndex);
        if (index >= json.length() || json.startsWith("null", index)) {
            return Map.of();
        }
        if (json.charAt(index) != '{') {
            throw new IllegalArgumentException("Expected object for " + field);
        }
        index++;
        Map<String, String> labels = new LinkedHashMap<>();
        while (index < json.length()) {
            index = skipWs(json, index);
            if (index < json.length() && json.charAt(index) == '}') {
                return labels;
            }
            if (index >= json.length() || json.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected string key in " + field);
            }
            String key = readJsonString(json, index);
            index = skipAfterString(json, index);
            index = skipWs(json, index);
            if (index >= json.length() || json.charAt(index) != ':') {
                throw new IllegalArgumentException("Expected ':' in " + field);
            }
            index = skipWs(json, index + 1);
            if (index >= json.length() || json.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected string value in " + field);
            }
            String value = readJsonString(json, index);
            labels.put(key, value);
            index = skipAfterString(json, index);
            index = skipWs(json, index);
            if (index < json.length() && json.charAt(index) == ',') {
                index++;
                continue;
            }
            if (index < json.length() && json.charAt(index) == '}') {
                return labels;
            }
            throw new IllegalArgumentException("Malformed object for " + field);
        }
        throw new IllegalArgumentException("Unterminated object for " + field);
    }

    private static int indexAfterKey(String json, String field) {
        int index = skipWs(json, 0);
        if (index >= json.length() || json.charAt(index) != '{') {
            return -1;
        }
        index = skipWs(json, index + 1);
        while (index < json.length()) {
            if (json.charAt(index) == '}') {
                return -1;
            }
            if (json.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected object key");
            }
            String key = readJsonString(json, index);
            index = skipAfterString(json, index);
            index = skipWs(json, index);
            if (index >= json.length() || json.charAt(index) != ':') {
                throw new IllegalArgumentException("Expected ':' after key");
            }
            index = skipWs(json, index + 1);
            if (field.equals(key)) {
                return index;
            }
            index = skipValue(json, index);
            index = skipWs(json, index);
            if (index < json.length() && json.charAt(index) == ',') {
                index = skipWs(json, index + 1);
            }
        }
        return -1;
    }

    /** Skip one JSON value starting at {@code index} (already at value start). */
    private static int skipValue(String json, int index) {
        index = skipWs(json, index);
        if (index >= json.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON value");
        }
        char c = json.charAt(index);
        if (c == '"') {
            return skipAfterString(json, index);
        }
        if (c == '{') {
            return skipContainer(json, index, '{', '}');
        }
        if (c == '[') {
            return skipContainer(json, index, '[', ']');
        }
        if (json.startsWith("null", index)) {
            return index + 4;
        }
        if (json.startsWith("true", index)) {
            return index + 4;
        }
        if (json.startsWith("false", index)) {
            return index + 5;
        }
        int end = index;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if ((ch >= '0' && ch <= '9')
                    || ch == '-'
                    || ch == '+'
                    || ch == '.'
                    || ch == 'e'
                    || ch == 'E') {
                end++;
            } else {
                break;
            }
        }
        if (end == index) {
            throw new IllegalArgumentException("Expected JSON value");
        }
        return end;
    }

    private static int skipContainer(String json, int openIndex, char open, char close) {
        if (json.charAt(openIndex) != open) {
            throw new IllegalArgumentException("Expected " + open);
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIndex; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        throw new IllegalArgumentException("Unterminated container");
    }

    private static int skipWs(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static String readJsonString(String json, int quoteIndex) {
        if (json.charAt(quoteIndex) != '"') {
            throw new IllegalArgumentException("Expected opening quote");
        }
        StringBuilder sb = new StringBuilder();
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char ch = json.charAt(index++);
            if (ch == '"') {
                return sb.toString();
            }
            if (ch == '\\') {
                if (index >= json.length()) {
                    throw new IllegalArgumentException("Unterminated escape");
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> sb.append(escaped);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (index + 4 > json.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        String hex = json.substring(index, index + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Unsupported escape: \\" + escaped);
                }
                continue;
            }
            sb.append(ch);
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private static int skipAfterString(String json, int quoteIndex) {
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char ch = json.charAt(index++);
            if (ch == '"') {
                return index;
            }
            if (ch == '\\' && index < json.length()) {
                index++;
            }
        }
        return index;
    }
}
