package io.pingui.telemetry;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * High-frequency telemetry sample for the bus (P16-010 / ADR_TELEMETRY).
 *
 * <p>JSON contract is shared with Python {@code MetricSample} ({@code kind=sample}).
 */
public record MetricSample(
        String name, double value, String host, Integer hop, Map<String, String> labels, Instant timestamp) {
    public static final String KIND = "sample";

    public MetricSample {
        name = TelemetryJson.requireNonBlank(name, "name");
        host = TelemetryJson.requireNonBlank(host, "host");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be a finite number");
        }
        if (hop != null && hop < 1) {
            throw new IllegalArgumentException("hop must be >= 1 when present");
        }
        labels = TelemetryJson.copyLabels(labels);
        timestamp = Objects.requireNonNullElseGet(timestamp, Instant::now);
    }

    /** Convenience factory for {@code pingui_rtt_ms} hop samples. */
    public static MetricSample rttMs(
            String host, int hop, double rttMs, Map<String, String> labels, Instant timestamp) {
        return new MetricSample("pingui_rtt_ms", rttMs, host, hop, labels, timestamp);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"kind\":").append(TelemetryJson.quote(KIND));
        sb.append(",\"name\":").append(TelemetryJson.quote(name));
        sb.append(",\"value\":").append(Double.toString(value));
        sb.append(",\"host\":").append(TelemetryJson.quote(host));
        if (hop != null) {
            sb.append(",\"hop\":").append(hop);
        } else {
            sb.append(",\"hop\":null");
        }
        sb.append(",\"labels\":").append(TelemetryJson.labelsObject(labels));
        sb.append(",\"timestamp\":").append(TelemetryJson.quote(timestamp.toString()));
        sb.append('}');
        return sb.toString();
    }

    public static MetricSample fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON payload is empty");
        }
        String kind = TelemetryJson.readOptionalStringField(json, "kind");
        if (kind != null && !KIND.equals(kind)) {
            throw new IllegalArgumentException("Unsupported kind: " + kind);
        }
        String nameValue = TelemetryJson.readStringField(json, "name");
        Double value = TelemetryJson.readOptionalNumberField(json, "value");
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be a finite number");
        }
        String hostValue = TelemetryJson.readStringField(json, "host");
        Double hopNumber = TelemetryJson.readOptionalNumberField(json, "hop");
        Integer hopValue = null;
        if (hopNumber != null) {
            if (!Double.isFinite(hopNumber) || hopNumber != Math.rint(hopNumber)) {
                throw new IllegalArgumentException("hop must be an integer or null");
            }
            hopValue = hopNumber.intValue();
        }
        Map<String, String> labelsValue = TelemetryJson.readLabelsObject(json, "labels");
        String timestampRaw = TelemetryJson.readStringField(json, "timestamp");
        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampRaw.replace("Z", "+00:00"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid timestamp: " + timestampRaw, ex);
        }
        return new MetricSample(nameValue, value, hostValue, hopValue, labelsValue, timestamp);
    }
}
