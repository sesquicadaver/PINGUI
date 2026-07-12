package io.pingui.persistence.timeseries;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InfluxDB 2.x writer via HTTP line protocol (Python InfluxTimeSeriesBackend parity).
 *
 * <p>Token is never logged.
 */
public final class InfluxTimeSeriesBackend implements TimeSeriesBackend {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxTimeSeriesBackend.class);

    static final String PING_MEASUREMENT = "pingui_rtt";
    static final String ROUTE_MEASUREMENT = "pingui_route";

    private final String writeUrl;
    private final String token;
    private final HttpClient httpClient;
    private volatile boolean closed;

    public InfluxTimeSeriesBackend(String url, String token, String org, String bucket) {
        this(
                url,
                token,
                org,
                bucket,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    InfluxTimeSeriesBackend(String url, String token, String org, String bucket, HttpClient httpClient) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(org, "org");
        Objects.requireNonNull(bucket, "bucket");
        this.token = token;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.writeUrl = base + "/api/v2/write?org=" + encode(org) + "&bucket=" + encode(bucket) + "&precision=ns";
    }

    @Override
    public void writePingSamples(List<PingSample> samples) {
        if (samples == null || samples.isEmpty() || closed) {
            return;
        }
        String body =
                samples.stream().map(InfluxTimeSeriesBackend::formatPingLine).collect(Collectors.joining("\n"));
        postLines(body);
    }

    @Override
    public void writeRouteEvent(RouteEvent event) {
        if (event == null || closed) {
            return;
        }
        postLines(formatRouteLine(event));
    }

    @Override
    public void close() {
        closed = true;
    }

    static String formatPingLine(PingSample sample) {
        return PING_MEASUREMENT
                + ",target="
                + escapeTag(sample.targetHost())
                + ",hop_ip="
                + escapeTag(sample.hopIp())
                + " hop="
                + sample.hop()
                + "i,rtt_ms="
                + sample.rttMs()
                + " "
                + sample.observedAt().toEpochMilli() * 1_000_000L;
    }

    static String formatRouteLine(RouteEvent event) {
        return ROUTE_MEASUREMENT
                + ",target="
                + escapeTag(event.targetHost())
                + " route_ips=\""
                + escapeField(String.join(",", event.routeIps()))
                + "\",route_changed="
                + (event.routeChanged() ? 1 : 0)
                + "i "
                + event.observedAt().toEpochMilli() * 1_000_000L;
    }

    private void postLines(String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(writeUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                LOG.warn("InfluxDB write failed: HTTP {} (body redacted)", code);
            }
        } catch (IOException ex) {
            LOG.warn("InfluxDB write failed: {}", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("InfluxDB write interrupted");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Escape Influx line-protocol tag value. */
    static String escapeTag(String value) {
        return value.replace("\\", "\\\\")
                .replace(" ", "\\ ")
                .replace(",", "\\,")
                .replace("=", "\\=");
    }

    /** Escape Influx line-protocol string field. */
    static String escapeField(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
