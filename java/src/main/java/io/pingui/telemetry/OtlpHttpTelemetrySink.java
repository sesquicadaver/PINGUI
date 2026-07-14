package io.pingui.telemetry;

import io.pingui.config.TelemetryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OTLP/HTTP JSON exporter for logs and metrics (P16-080 / ADR_TELEMETRY).
 *
 * <p>No OpenTelemetry SDK — posts ExportLogsServiceRequest / ExportMetricsServiceRequest JSON to
 * {@code /v1/logs} and {@code /v1/metrics}. Events always become log records; samples become gauge
 * data points only when {@link #eventsOnly()} is {@code false}. Failures are logged; methods never
 * throw into the poll / bus path.
 */
public final class OtlpHttpTelemetrySink implements TelemetrySink {
    public static final String ID = "otlp";
    public static final String LOGS_PATH = "/v1/logs";
    public static final String METRICS_PATH = "/v1/metrics";
    public static final String DEFAULT_SERVICE_NAME = "pingui";
    public static final String SCOPE_NAME = "io.pingui.telemetry";
    /** OTLP SeverityNumber INFO. */
    public static final int SEVERITY_INFO = 9;
    /** OTLP SeverityNumber WARN. */
    public static final int SEVERITY_WARN = 13;

    private static final Logger LOG = Logger.getLogger(OtlpHttpTelemetrySink.class.getName());

    private final URI logsUri;
    private final URI metricsUri;
    private final String serviceName;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final boolean eventsOnly;

    public OtlpHttpTelemetrySink(String endpoint) {
        this(endpoint, DEFAULT_SERVICE_NAME, SinkConfig.defaults());
    }

    public OtlpHttpTelemetrySink(String endpoint, String serviceName, SinkConfig sinkConfig) {
        this(
                endpoint,
                serviceName,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(10),
                sinkConfig);
    }

    public OtlpHttpTelemetrySink(
            String endpoint, String serviceName, HttpClient httpClient, Duration timeout, SinkConfig sinkConfig) {
        String base = normalizeEndpoint(endpoint);
        this.logsUri = URI.create(base + LOGS_PATH);
        this.metricsUri = URI.create(base + METRICS_PATH);
        this.serviceName = requireNonBlank(serviceName, "serviceName");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.eventsOnly = SinkConfig.require(sinkConfig).eventsOnly();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean eventsOnly() {
        return eventsOnly;
    }

    @Override
    public void onSample(MetricSample sample) {
        if (eventsOnly || sample == null) {
            return;
        }
        post(metricsUri, formatMetricsBody(sample));
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        post(logsUri, formatLogsBody(event));
    }

    @Override
    public void close() {
        // HttpClient is shared / not owned
    }

    /** Package-visible for tests. */
    String formatLogsBody(TelemetryEvent event) {
        Instant ts = event.timestamp() != null ? event.timestamp() : Instant.now();
        int severity = severityFor(event.event());
        StringBuilder sb = new StringBuilder(384);
        sb.append("{\"resourceLogs\":[{\"resource\":{\"attributes\":[");
        appendAttr(sb, "service.name", serviceName, true);
        sb.append("]},\"scopeLogs\":[{\"scope\":{");
        sb.append(TelemetryJson.quote("name")).append(':').append(TelemetryJson.quote(SCOPE_NAME));
        sb.append("},\"logRecords\":[{");
        sb.append(TelemetryJson.quote("timeUnixNano")).append(':').append(TelemetryJson.quote(nanosString(ts)));
        sb.append(',').append(TelemetryJson.quote("severityNumber")).append(':').append(severity);
        sb.append(',').append(TelemetryJson.quote("body")).append(":{");
        sb.append(TelemetryJson.quote("stringValue")).append(':').append(TelemetryJson.quote(event.toJson()));
        sb.append("},\"attributes\":[");
        appendAttr(sb, "event", event.event(), true);
        appendAttr(sb, "host", event.host(), false);
        for (Map.Entry<String, String> label : event.labels().entrySet()) {
            appendAttr(sb, label.getKey(), label.getValue(), false);
        }
        sb.append("]}]}]}]}");
        return sb.toString();
    }

    /** Package-visible for tests. */
    String formatMetricsBody(MetricSample sample) {
        Instant ts = sample.timestamp() != null ? sample.timestamp() : Instant.now();
        StringBuilder sb = new StringBuilder(384);
        sb.append("{\"resourceMetrics\":[{\"resource\":{\"attributes\":[");
        appendAttr(sb, "service.name", serviceName, true);
        sb.append("]},\"scopeMetrics\":[{\"scope\":{");
        sb.append(TelemetryJson.quote("name")).append(':').append(TelemetryJson.quote(SCOPE_NAME));
        sb.append("},\"metrics\":[{");
        sb.append(TelemetryJson.quote("name")).append(':').append(TelemetryJson.quote(sample.name()));
        sb.append(",\"gauge\":{\"dataPoints\":[{");
        sb.append(TelemetryJson.quote("asDouble")).append(':').append(Double.toString(sample.value()));
        sb.append(',')
                .append(TelemetryJson.quote("timeUnixNano"))
                .append(':')
                .append(TelemetryJson.quote(nanosString(ts)));
        sb.append(",\"attributes\":[");
        appendAttr(sb, "host", sample.host(), true);
        if (sample.hop() != null) {
            sb.append(',').append('{');
            sb.append(TelemetryJson.quote("key")).append(':').append(TelemetryJson.quote("hop"));
            sb.append(',').append(TelemetryJson.quote("value")).append(":{");
            sb.append(TelemetryJson.quote("intValue"))
                    .append(':')
                    .append(TelemetryJson.quote(Integer.toString(sample.hop())));
            sb.append("}}");
        }
        for (Map.Entry<String, String> label : sample.labels().entrySet()) {
            appendAttr(sb, label.getKey(), label.getValue(), false);
        }
        sb.append("]}]}]}]}]}");
        return sb.toString();
    }

    URI logsUri() {
        return logsUri;
    }

    URI metricsUri() {
        return metricsUri;
    }

    String serviceName() {
        return serviceName;
    }

    static String normalizeEndpoint(String endpoint) {
        String raw = requireNonBlank(endpoint, "endpoint");
        String trimmed = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (trimmed.endsWith(LOGS_PATH)) {
            return trimmed.substring(0, trimmed.length() - LOGS_PATH.length());
        }
        if (trimmed.endsWith(METRICS_PATH)) {
            return trimmed.substring(0, trimmed.length() - METRICS_PATH.length());
        }
        return trimmed;
    }

    static String nanosString(Instant instant) {
        long nanos = Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L);
        return Long.toString(Math.addExact(nanos, instant.getNano()));
    }

    static int severityFor(String eventType) {
        if (TelemetryEvent.PROBE_ERROR.equals(eventType)) {
            return SEVERITY_WARN;
        }
        return SEVERITY_INFO;
    }

    private void post(URI uri, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                LOG.log(Level.WARNING, "OtlpHttpTelemetrySink HTTP {0} to {1}", new Object[] {
                    code, TelemetryConfig.redactUrl(uri.toString())
                });
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(
                    Level.WARNING,
                    "OtlpHttpTelemetrySink write failed to " + TelemetryConfig.redactUrl(uri.toString()),
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.log(
                    Level.WARNING,
                    "OtlpHttpTelemetrySink interrupted for " + TelemetryConfig.redactUrl(uri.toString()));
        }
    }

    private static void appendAttr(StringBuilder sb, String key, String value, boolean first) {
        if (!first) {
            sb.append(',');
        }
        sb.append('{');
        sb.append(TelemetryJson.quote("key")).append(':').append(TelemetryJson.quote(key));
        sb.append(',').append(TelemetryJson.quote("value")).append(":{");
        sb.append(TelemetryJson.quote("stringValue")).append(':').append(TelemetryJson.quote(value));
        sb.append("}}");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
