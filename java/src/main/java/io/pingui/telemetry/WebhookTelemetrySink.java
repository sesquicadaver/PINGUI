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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remote webhook sink for {@code route_change} (P16-050 / ADR_TELEMETRY / ADR_ALERTS / P32-005).
 *
 * <p>Owns the single HTTP POST path used by {@link io.pingui.monitor.WebhookAlertDispatcher}.
 * Payload for {@link TelemetryEvent#ROUTE_CHANGE} (and P10 alert posts) is the ADR_ALERTS JSON
 * shape ({@code event}/{@code host}/{@code old_ips}/{@code new_ips}/{@code timestamp}/{@code profile})
 * — Slack-compatible, unchanged. Other events and samples are ignored ({@link #eventsOnly()} is
 * always {@code true}). HTTP runs on a bounded daemon executor so probe/UI threads never block on
 * network I/O. Failures are logged; methods never throw into the poll / bus path.
 */
public final class WebhookTelemetrySink implements TelemetrySink {
    public static final String ID = "webhook";

    private static final Logger LOG = Logger.getLogger(WebhookTelemetrySink.class.getName());
    private static final AtomicInteger POOL_SEQ = new AtomicInteger();

    private final String url;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final ExecutorService httpExecutor;
    private final boolean ownsExecutor;
    private final AtomicInteger inflight = new AtomicInteger();

    public WebhookTelemetrySink(String url) {
        this(url, HttpClient.newHttpClient(), Duration.ofSeconds(5), newWebhookPool(), true);
    }

    /**
     * Full constructor for tests (injectable client / timeout). Posts still run asynchronously on a
     * dedicated pool — call {@link #awaitIdleForTests(Duration)} before asserting.
     *
     * @param url non-blank webhook URL
     */
    public WebhookTelemetrySink(String url, HttpClient httpClient, Duration timeout) {
        this(url, httpClient, timeout, newWebhookPool(), true);
    }

    WebhookTelemetrySink(
            String url, HttpClient httpClient, Duration timeout, ExecutorService httpExecutor, boolean ownsExecutor) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook URL is required");
        }
        this.url = url.strip();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.httpExecutor = Objects.requireNonNull(httpExecutor, "httpExecutor");
        this.ownsExecutor = ownsExecutor;
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
        postJson(
                formatRouteChangeAlertJson(event.host(), event.oldIps(), event.newIps(), event.timestamp(), profile),
                event.host());
    }

    /**
     * P10 alert path: enqueue ADR_ALERTS JSON body on the webhook executor (off probe/UI thread).
     *
     * @param jsonBody ADR_ALERTS route_change JSON
     * @param host host label for warning logs
     */
    public void postJson(String jsonBody, String host) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return;
        }
        String hostLabel = host == null || host.isBlank() ? "?" : host;
        inflight.incrementAndGet();
        try {
            httpExecutor.execute(() -> {
                try {
                    postJsonSync(jsonBody, hostLabel);
                } finally {
                    inflight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException ex) {
            inflight.decrementAndGet();
            LOG.log(Level.WARNING, "WebhookTelemetrySink rejected post for {0} ({1})", new Object[] {
                hostLabel, redactedUrl()
            });
        }
    }

    /** Blocks until queued HTTP posts complete (tests). */
    public void awaitIdleForTests(Duration maxWait) throws InterruptedException {
        Duration wait = maxWait != null ? maxWait : Duration.ofSeconds(5);
        long deadline = System.nanoTime() + wait.toNanos();
        while (inflight.get() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        if (inflight.get() > 0) {
            throw new IllegalStateException("webhook posts still in flight after " + wait);
        }
    }

    private void postJsonSync(String jsonBody, String hostLabel) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                LOG.log(Level.WARNING, "WebhookTelemetrySink HTTP {0} for {1} ({2})", new Object[] {
                    response.statusCode(), hostLabel, redactedUrl()
                });
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
            LOG.log(Level.WARNING, "WebhookTelemetrySink interrupted for {0} ({1})", new Object[] {
                hostLabel, redactedUrl()
            });
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

    @Override
    public void close() {
        if (!ownsExecutor) {
            return;
        }
        httpExecutor.shutdown();
        try {
            if (!httpExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                httpExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            httpExecutor.shutdownNow();
        }
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

    private static ExecutorService newWebhookPool() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pingui-webhook-" + POOL_SEQ.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(2, factory);
    }
}
