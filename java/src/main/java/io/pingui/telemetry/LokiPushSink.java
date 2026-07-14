package io.pingui.telemetry;

import io.pingui.config.TelemetryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remote Grafana Loki push sink (P16-032 / ADR_TELEMETRY / SPIKE_LOG_SINKS).
 *
 * <p>POSTs one event per request to {@code /loki/api/v1/push}. Stream labels are a fixed
 * low-cardinality set: {@code job=pingui}, {@code site}, {@code host}. The log line is
 * {@link TelemetryEvent#toJson()} (or {@link MetricSample#toJson()} when {@code events_only=false}).
 * {@link #eventsOnly()} comes from {@link SinkConfig} (default {@code true}). Failures are logged;
 * methods never throw into the poll / bus path.
 */
public final class LokiPushSink implements TelemetrySink {
    public static final String ID = "loki";
    public static final String JOB = "pingui";
    public static final String PUSH_PATH = "/loki/api/v1/push";

    private static final Logger LOG = Logger.getLogger(LokiPushSink.class.getName());

    private final URI pushUri;
    private final String site;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final boolean eventsOnly;

    public LokiPushSink(String pushUrl, String site) {
        this(pushUrl, site, SinkConfig.defaults());
    }

    public LokiPushSink(String pushUrl, String site, SinkConfig sinkConfig) {
        this(
                pushUrl,
                site,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(10),
                sinkConfig);
    }

    /**
     * Full constructor for tests (injectable client / timeout).
     *
     * @param pushUrl full Loki push URL or base URL (path appended when missing)
     * @param site operator site label (non-blank)
     */
    public LokiPushSink(String pushUrl, String site, HttpClient httpClient, Duration timeout) {
        this(pushUrl, site, httpClient, timeout, SinkConfig.defaults());
    }

    public LokiPushSink(String pushUrl, String site, HttpClient httpClient, Duration timeout, SinkConfig sinkConfig) {
        this.pushUri = URI.create(normalizePushUrl(pushUrl));
        this.site = requireNonBlank(site, "site");
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
        postBody(formatSamplePushBody(sample));
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        postBody(formatPushBody(event));
    }

    private void postBody(String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(pushUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                LOG.log(Level.WARNING, "LokiPushSink HTTP {0} to {1}", new Object[] {code, redactedUri()});
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "LokiPushSink write failed to " + redactedUri(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "LokiPushSink interrupted for " + redactedUri());
        }
    }

    /** Builds one Loki push JSON body. Package-visible for tests. */
    String formatPushBody(TelemetryEvent event) {
        Instant ts = event.timestamp() != null ? event.timestamp() : Instant.now();
        return formatPush(event.host(), ts, event.toJson());
    }

    /** Sample push body when {@code events_only=false} (lab). Package-visible for tests. */
    String formatSamplePushBody(MetricSample sample) {
        Instant ts = sample.timestamp() != null ? sample.timestamp() : Instant.now();
        return formatPush(sample.host(), ts, sample.toJson());
    }

    private String formatPush(String hostLabel, Instant ts, String line) {
        String ns = nanosString(ts);
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"streams\":[{\"stream\":{");
        sb.append(TelemetryJson.quote("job")).append(':').append(TelemetryJson.quote(JOB));
        sb.append(',').append(TelemetryJson.quote("site")).append(':').append(TelemetryJson.quote(site));
        sb.append(',').append(TelemetryJson.quote("host")).append(':').append(TelemetryJson.quote(hostLabel));
        sb.append("},\"values\":[[");
        sb.append(TelemetryJson.quote(ns)).append(',').append(TelemetryJson.quote(line));
        sb.append("]]}]}");
        return sb.toString();
    }

    /** Exposes the resolved push URI for tests. */
    URI pushUri() {
        return pushUri;
    }

    String site() {
        return site;
    }

    static String normalizePushUrl(String pushUrl) {
        String raw = requireNonBlank(pushUrl, "pushUrl");
        String trimmed = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (trimmed.contains(PUSH_PATH)) {
            return trimmed;
        }
        return trimmed + PUSH_PATH;
    }

    static String nanosString(Instant instant) {
        long nanos = Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L);
        return Long.toString(Math.addExact(nanos, instant.getNano()));
    }

    private String redactedUri() {
        return TelemetryConfig.redactUrl(pushUri.toString());
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
