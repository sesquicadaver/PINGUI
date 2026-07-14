package io.pingui.telemetry;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rare telemetry event for the bus (P16-010 / ADR_TELEMETRY).
 *
 * <p>JSON contract is shared with Python {@code TelemetryEvent} ({@code kind=event}).
 * Optional {@code old_ips}/{@code new_ips} mirror alert route-change fields for LOG sinks.
 */
public record TelemetryEvent(
        String event,
        String host,
        Map<String, String> labels,
        String message,
        List<String> oldIps,
        List<String> newIps,
        Instant timestamp) {
    public static final String KIND = "event";
    public static final String ROUTE_CHANGE = "route_change";
    public static final String PROBE_ERROR = "probe_error";
    public static final String DAEMON_START = "daemon_start";
    /** 5m (or custom-window) avg/max RTT per hop for optional LOG (P16-034). */
    public static final String RTT_AGGREGATE = "rtt_aggregate";

    public TelemetryEvent {
        event = TelemetryJson.requireNonBlank(event, "event");
        host = TelemetryJson.requireNonBlank(host, "host");
        labels = TelemetryJson.copyLabels(labels);
        message = message == null || message.isBlank() ? null : message;
        oldIps = oldIps == null ? List.of() : List.copyOf(oldIps);
        newIps = newIps == null ? List.of() : List.copyOf(newIps);
        timestamp = Objects.requireNonNullElseGet(timestamp, Instant::now);
    }

    public static TelemetryEvent routeChange(
            String host, List<String> oldIps, List<String> newIps, Map<String, String> labels, Instant timestamp) {
        return new TelemetryEvent(ROUTE_CHANGE, host, labels, null, oldIps, newIps, timestamp);
    }

    public static TelemetryEvent probeError(
            String host, String message, Map<String, String> labels, Instant timestamp) {
        return new TelemetryEvent(PROBE_ERROR, host, labels, message, List.of(), List.of(), timestamp);
    }

    /**
     * Builds an RTT aggregate event for LOG sinks (P16-034).
     *
     * @param message JSON payload from {@link AggregateTelemetryJob} (hop/avg/max/count/window)
     */
    public static TelemetryEvent rttAggregate(
            String host, String message, Map<String, String> labels, Instant timestamp) {
        return new TelemetryEvent(RTT_AGGREGATE, host, labels, message, List.of(), List.of(), timestamp);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(192);
        sb.append("{\"kind\":").append(TelemetryJson.quote(KIND));
        sb.append(",\"event\":").append(TelemetryJson.quote(event));
        sb.append(",\"host\":").append(TelemetryJson.quote(host));
        sb.append(",\"labels\":").append(TelemetryJson.labelsObject(labels));
        if (message != null) {
            sb.append(",\"message\":").append(TelemetryJson.quote(message));
        } else {
            sb.append(",\"message\":null");
        }
        sb.append(",\"old_ips\":").append(TelemetryJson.stringArray(oldIps));
        sb.append(",\"new_ips\":").append(TelemetryJson.stringArray(newIps));
        sb.append(",\"timestamp\":").append(TelemetryJson.quote(timestamp.toString()));
        sb.append('}');
        return sb.toString();
    }

    public static TelemetryEvent fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON payload is empty");
        }
        String kind = TelemetryJson.readOptionalStringField(json, "kind");
        if (kind != null && !KIND.equals(kind)) {
            throw new IllegalArgumentException("Unsupported kind: " + kind);
        }
        String eventValue = TelemetryJson.readStringField(json, "event");
        String hostValue = TelemetryJson.readStringField(json, "host");
        Map<String, String> labelsValue = TelemetryJson.readLabelsObject(json, "labels");
        String messageValue = TelemetryJson.readOptionalStringField(json, "message");
        List<String> oldIpsValue = TelemetryJson.readOptionalStringArray(json, "old_ips");
        List<String> newIpsValue = TelemetryJson.readOptionalStringArray(json, "new_ips");
        String timestampRaw = TelemetryJson.readStringField(json, "timestamp");
        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampRaw.replace("Z", "+00:00"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid timestamp: " + timestampRaw, ex);
        }
        return new TelemetryEvent(
                eventValue,
                hostValue,
                labelsValue,
                messageValue,
                oldIpsValue == null ? List.of() : oldIpsValue,
                newIpsValue == null ? List.of() : newIpsValue,
                timestamp);
    }
}
