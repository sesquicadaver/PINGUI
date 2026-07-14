package io.pingui.telemetry;

import io.pingui.config.TelemetryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remote webhook sink for {@code route_change} (P16-050 / ADR_TELEMETRY / ADR_ALERTS).
 *
 * <p>Owns the single HTTP POST path used by {@link io.pingui.monitor.WebhookAlertDispatcher}.
 * Payload for {@link TelemetryEvent#ROUTE_CHANGE} (and P10 alert posts) is the ADR_ALERTS JSON
 * shape ({@code event}/{@code host}/{@code old_ips}/{@code new_ips}/{@code timestamp}/{@code profile})
 * — Slack-compatible, unchanged. Other events and samples are ignored ({@link #eventsOnly()} is
 * always {@code true}). Failures are logged; methods never throw into the poll / bus path.
 */
public final class WebhookTelemetrySink implements TelemetrySink {
    public static final String ID = "webhook";

    private static final Logger LOG = Logger.getLogger(WebhookTelemetrySink.class.getName());

    private final String url;
    private final HttpClient httpClient;
    private final Duration timeout;

    public WebhookTelemetrySink(String url) {
        this(url, HttpClient.newHttpClient(), Duration.ofSeconds(5));
    }

    /**
     * Full constructor for tests (injectable client / timeout).
     *
     * @param url non-blank webhook URL
     */
    public WebhookTelemetrySink(String url, HttpClient httpClient, Duration timeout) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook URL is required");
        }
        this.url = url.strip();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean eventsOnly() {
        return true;
    }

    @Override
    public void onSample(MetricSample sample) {
        // Webhooks are operator notify / rare events only — never high-freq RTT.
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null || !TelemetryEvent.ROUTE_CHANGE.equals(event.event())) {
            return;
        }
        String profile = event.labels().get(MetricNames.LABEL_PROFILE);
        if (profile == null || profile.isBlank()) {
            profile = "default";
        }
        postJson(formatRouteChangeAlertJson(event.host(), event.oldIps(), event.newIps(), event.timestamp(), profile), event.host());
    }

    /**
     * P10 alert path: POST a pre-built ADR_ALERTS JSON body via the same HTTP client as {@link #onEvent}.
     *
     * @param jsonBody ADR_ALERTS route_change JSON
     * @param host host label for warning logs
     */
    public void postJson(String jsonBody, String host) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return;
        }
        String hostLabel = host == null || host.isBlank() ? "?" : host;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                LOG.log(
                        Level.WARNING,
                        "WebhookTelemetrySink HTTP {0} for {1} ({2})",
                        new Object[] {response.statusCode(), hostLabel, redactedUrl()});
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(
                    Level.WARNING,
                    "WebhookTelemetrySink write failed for "
                            + hostLabel
                            + " ("
                            + redactedUrl()
                            + "): "
                            + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.log(
                    Level.WARNING,
                    "WebhookTelemetrySink interrupted for {0} ({1})",
                    new Object[] {hostLabel, redactedUrl()});
        }
    }

    /** Resolves the configured webhook URL (tests / diagnostics). */
    public String url() {
        return url;
    }

    /** Log-safe URL (no credentials / query). */
    public String redactedUrl() {
        return TelemetryConfig.redactUrl(url);
    }

    /** ADR_ALERTS route_change JSON (package-visible for tests). */
    static String formatRouteChangeAlertJson(
            String host, List<String> oldIps, List<String> newIps, Instant timestamp, String profile) {
        Instant ts = timestamp != null ? timestamp : Instant.now();
        String safeProfile = profile == null || profile.isBlank() ? "default" : profile;
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"event\":").append(TelemetryJson.quote(TelemetryEvent.ROUTE_CHANGE));
        sb.append(",\"host\":").append(TelemetryJson.quote(host));
        sb.append(",\"old_ips\":").append(TelemetryJson.stringArray(oldIps == null ? List.of() : oldIps));
        sb.append(",\"new_ips\":").append(TelemetryJson.stringArray(newIps == null ? List.of() : newIps));
        sb.append(",\"timestamp\":").append(TelemetryJson.quote(ts.toString()));
        sb.append(",\"profile\":").append(TelemetryJson.quote(safeProfile));
        sb.append('}');
        return sb.toString();
    }
}
