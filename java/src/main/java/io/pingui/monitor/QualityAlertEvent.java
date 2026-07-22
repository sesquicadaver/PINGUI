package io.pingui.monitor;

import io.pingui.config.ConfigError;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Quality alert payload ({@code endpoint_down}) per ADR_ALERT_RULES (P21-002). */
public record QualityAlertEvent(
        String event,
        String state,
        String host,
        Instant timestamp,
        String profile,
        String rule,
        Map<String, Object> detail) {
    public static final String EVENT_ENDPOINT_DOWN = "endpoint_down";
    public static final String STATE_FIRING = "firing";
    public static final String STATE_RESOLVED = "resolved";

    public QualityAlertEvent {
        event = requireNonBlank(event, "event");
        state = requireNonBlank(state, "state");
        if (!STATE_FIRING.equals(state) && !STATE_RESOLVED.equals(state)) {
            throw new IllegalArgumentException("state must be firing or resolved");
        }
        host = requireNonBlank(host, "host");
        timestamp = timestamp != null ? timestamp : Instant.now();
        profile = profile == null || profile.isBlank() ? "default" : profile;
        rule = requireNonBlank(rule, "rule");
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    public static QualityAlertEvent endpointDownFiring(
            String host, String profile, Instant timestamp, Map<String, Object> detail) {
        return new QualityAlertEvent(
                EVENT_ENDPOINT_DOWN, STATE_FIRING, host, timestamp, profile, EVENT_ENDPOINT_DOWN, detail);
    }

    public static QualityAlertEvent endpointDownResolved(
            String host, String profile, Instant timestamp, Map<String, Object> detail) {
        return new QualityAlertEvent(
                EVENT_ENDPOINT_DOWN, STATE_RESOLVED, host, timestamp, profile, EVENT_ENDPOINT_DOWN, detail);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"event\":").append(quote(event));
        sb.append(",\"state\":").append(quote(state));
        sb.append(",\"host\":").append(quote(host));
        sb.append(",\"timestamp\":").append(quote(timestamp.toString()));
        sb.append(",\"profile\":").append(quote(profile));
        sb.append(",\"rule\":").append(quote(rule));
        sb.append(",\"detail\":").append(detailObject(detail));
        sb.append('}');
        return sb.toString();
    }

    public String desktopTitle() {
        return "PINGUI " + event;
    }

    public String desktopBody() {
        return host + ": " + state;
    }

    private static String detailObject(Map<String, Object> detail) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            if (value instanceof Number number) {
                sb.append(number);
            } else if (value instanceof Boolean bool) {
                sb.append(bool);
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append(quote(Objects.toString(value)));
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigError(field + " must be a non-empty string");
        }
        return value;
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

    /** Convenience detail map builder for engine emits. */
    static Map<String, Object> detailOf(int failAfter, int failStreak, int clearAfter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fail_after", failAfter);
        map.put("fail_streak", failStreak);
        map.put("clear_after", clearAfter);
        return map;
    }
}
