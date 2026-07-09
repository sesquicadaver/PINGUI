package io.pingui.monitor;

import io.pingui.config.ConfigError;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Alert payload for a detected route change (P10-010 / ADR_ALERTS). */
public record RouteChangeEvent(
        String host, List<String> oldIps, List<String> newIps, Instant timestamp, String profile) {
    public static final String EVENT_TYPE = "route_change";

    public RouteChangeEvent {
        host = requireNonBlank(host, "host");
        oldIps = List.copyOf(oldIps != null ? oldIps : List.of());
        newIps = List.copyOf(newIps != null ? newIps : List.of());
        timestamp = timestamp != null ? timestamp : Instant.now();
        profile = profile == null || profile.isBlank() ? "default" : profile;
    }

    public static RouteChangeEvent fromRouteChange(
            String host, List<String> oldIps, List<String> newIps, String profile, Instant timestamp) {
        return new RouteChangeEvent(host, oldIps, newIps, timestamp, profile);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"event\":").append(quote(EVENT_TYPE));
        sb.append(",\"host\":").append(quote(host));
        sb.append(",\"old_ips\":").append(stringArray(oldIps));
        sb.append(",\"new_ips\":").append(stringArray(newIps));
        sb.append(",\"timestamp\":").append(quote(timestamp.toString()));
        sb.append(",\"profile\":").append(quote(profile));
        sb.append('}');
        return sb.toString();
    }

    public static RouteChangeEvent fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON payload is empty");
        }
        String event = readStringField(json, "event");
        if (!EVENT_TYPE.equals(event)) {
            throw new IllegalArgumentException("Unsupported event type: " + event);
        }
        String hostValue = readStringField(json, "host");
        List<String> oldIps = readStringArray(json, "old_ips");
        List<String> newIps = readStringArray(json, "new_ips");
        String timestampRaw = readStringField(json, "timestamp");
        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampRaw.replace("Z", "+00:00"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid timestamp: " + timestampRaw, ex);
        }
        String profileValue = readOptionalStringField(json, "profile");
        return new RouteChangeEvent(hostValue, oldIps, newIps, timestamp, profileValue);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigError(field + " must be a non-empty string");
        }
        return value;
    }

    private static String stringArray(List<String> values) {
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

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append('"');
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
        sb.append('"');
        return sb.toString();
    }

    private static String readStringField(String json, String field) {
        String value = readOptionalStringField(json, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return value;
    }

    private static String readOptionalStringField(String json, String field) {
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);
        if (start < 0) {
            return null;
        }
        int index = start + key.length();
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (index >= json.length() || json.charAt(index) != '"') {
            throw new IllegalArgumentException("Expected string for " + field);
        }
        return readJsonString(json, index);
    }

    private static List<String> readStringArray(String json, String field) {
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        int index = start + key.length();
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (index >= json.length() || json.charAt(index) != '[') {
            throw new IllegalArgumentException("Expected array for " + field);
        }
        index++;
        List<String> values = new ArrayList<>();
        while (index < json.length()) {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
            if (index < json.length() && json.charAt(index) == ']') {
                return values;
            }
            if (index >= json.length() || json.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected string in " + field);
            }
            String value = readJsonString(json, index);
            values.add(value);
            index = skipAfterString(json, index);
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
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
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
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
