package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.pingui.monitor.RouteChangeEvent;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WebhookTelemetrySinkTest {
    @Test
    void idEventsOnlyAndRejectsBlankUrl() {
        WebhookTelemetrySink sink = new WebhookTelemetrySink("http://127.0.0.1/hook");
        assertEquals(WebhookTelemetrySink.ID, sink.id());
        assertTrue(sink.eventsOnly());
        assertThrows(IllegalArgumentException.class, () -> new WebhookTelemetrySink(" "));
        sink.close();
    }

    @Test
    void onEventPostsAdrAlertsRouteChangeJsonAndIgnoresSamplesAndProbeError() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            posts.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(204, -1);
            try (OutputStream out = exchange.getResponseBody()) {
                // empty 204
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/hook?secret=token";
            WebhookTelemetrySink sink =
                    new WebhookTelemetrySink(url, HttpClient.newHttpClient(), Duration.ofSeconds(5));
            Instant ts = Instant.parse("2026-07-14T07:30:00Z");
            sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 12.0, MetricNames.javaLabels("lab", "trace"), ts));
            sink.onEvent(TelemetryEvent.probeError("8.8.8.8", "timeout", MetricNames.javaLabels("lab", "trace"), ts));
            sink.onEvent(TelemetryEvent.routeChange(
                    "8.8.8.8",
                    List.of("10.0.0.1"),
                    List.of("8.8.8.8"),
                    MetricNames.javaLabels("lab", "trace"),
                    ts));
            assertEquals(1, posts.get());
            RouteChangeEvent parsed = RouteChangeEvent.fromJson(body.get());
            assertEquals("8.8.8.8", parsed.host());
            assertEquals(List.of("10.0.0.1"), parsed.oldIps());
            assertEquals(List.of("8.8.8.8"), parsed.newIps());
            assertEquals("lab", parsed.profile());
            assertEquals(ts, parsed.timestamp());
            assertEquals("http://127.0.0.1:" + port + "/hook", sink.redactedUrl());
            sink.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void formatMatchesRouteChangeEventToJson() {
        Instant ts = Instant.parse("2026-07-14T08:00:00Z");
        RouteChangeEvent event =
                RouteChangeEvent.fromRouteChange("1.1.1.1", List.of("10.0.0.1"), List.of("1.1.1.1"), "noc", ts);
        assertEquals(
                event.toJson(),
                WebhookTelemetrySink.formatRouteChangeAlertJson(
                        event.host(), event.oldIps(), event.newIps(), event.timestamp(), event.profile()));
    }

    @Test
    void postJsonUsesSharedHttpPath() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(204, -1);
            try (OutputStream out = exchange.getResponseBody()) {
                // empty
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WebhookTelemetrySink sink = new WebhookTelemetrySink(
                    "http://127.0.0.1:" + port + "/hook", HttpClient.newHttpClient(), Duration.ofSeconds(5));
            RouteChangeEvent event = RouteChangeEvent.fromRouteChange(
                    "1.1.1.1", List.of("10.0.0.1"), List.of("1.1.1.1"), "noc", Instant.parse("2026-07-14T08:00:00Z"));
            sink.postJson(event.toJson(), event.host());
            RouteChangeEvent parsed = RouteChangeEvent.fromJson(body.get());
            assertEquals(event.host(), parsed.host());
            assertEquals(event.profile(), parsed.profile());
            sink.close();
        } finally {
            server.stop(0);
        }
    }
}
