package io.pingui.export;

import io.pingui.persistence.SessionDatabase;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Cron-friendly dump of SQLite telemetry archive to CSV or JSON (P16-023).
 *
 * <p>Format is chosen from the output path extension ({@code .json} / {@code .csv}).
 */
public final class TelemetryDump {
    private static final String[] CSV_FIELDS = {
        "kind", "name_or_event", "value", "host", "hop", "message", "observed_at", "payload_json"
    };

    private TelemetryDump() {}

    /** Writes samples + events from {@code database} to {@code path}. */
    public static void export(SessionDatabase database, Path path) throws IOException {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(path, "path");
        List<MetricSample> samples = database.listAllTelemetrySamples();
        List<TelemetryEvent> events = database.listAllTelemetryEvents();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            Files.writeString(path, toCsv(samples, events), StandardCharsets.UTF_8);
        } else if (name.endsWith(".json")) {
            Files.writeString(path, toJson(samples, events), StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("--telemetry-dump path must end with .csv or .json");
        }
    }

    static String toJson(List<MetricSample> samples, List<TelemetryEvent> events) {
        StringBuilder sb = new StringBuilder(256 + samples.size() * 128 + events.size() * 128);
        sb.append("{\"samples\":[");
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(samples.get(i).toJson());
        }
        sb.append("],\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(events.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    static String toCsv(List<MetricSample> samples, List<TelemetryEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CSV_FIELDS)).append('\n');
        for (MetricSample sample : samples) {
            sb.append(csvRow(
                            "sample",
                            sample.name(),
                            Double.toString(sample.value()),
                            sample.host(),
                            sample.hop() == null ? "" : Integer.toString(sample.hop()),
                            "",
                            sample.timestamp().toString(),
                            sample.toJson()))
                    .append('\n');
        }
        for (TelemetryEvent event : events) {
            sb.append(csvRow(
                            "event",
                            event.event(),
                            "",
                            event.host(),
                            "",
                            event.message() == null ? "" : event.message(),
                            event.timestamp().toString(),
                            event.toJson()))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String csvRow(
            String kind,
            String nameOrEvent,
            String value,
            String host,
            String hop,
            String message,
            String observedAt,
            String payload) {
        return String.join(
                ",",
                csvCell(kind),
                csvCell(nameOrEvent),
                csvCell(value),
                csvCell(host),
                csvCell(hop),
                csvCell(message),
                csvCell(observedAt),
                csvCell(payload));
    }

    private static String csvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
