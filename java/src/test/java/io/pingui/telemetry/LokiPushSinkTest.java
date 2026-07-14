package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LokiPushSinkTest {
    @Test
    void eventsOnlyAndId() {
        LokiPushSink sink = new LokiPushSink("http://127.0.0.1:3100", "lab");
        assertEquals(LokiPushSink.ID, sink.id());
        assertTrue(sink.eventsOnly());
        assertEquals("lab", sink.site());
        assertTrue(sink.pushUri().toString().endsWith(LokiPushSink.PUSH_PATH));
        sink.close();
    }

    @Test
    void normalizeAppendsPushPath() {
        assertEquals("http://127.0.0.1:3100/loki/api/v1/push", LokiPushSink.normalizePushUrl("http://127.0.0.1:3100"));
        assertEquals(
                "http://127.0.0.1:3100/loki/api/v1/push",
                LokiPushSink.normalizePushUrl("http://127.0.0.1:3100/loki/api/v1/push"));
    }

    @Test
    void formatPushBodyHasJobSiteHostAndJsonLine() {
        Instant ts = Instant.parse("2026-07-14T06:00:00Z");
        LokiPushSink sink =
                new LokiPushSink("http://127.0.0.1:3100", "noc", HttpClient.newHttpClient(), Duration.ofSeconds(2));
        String body = sink.formatPushBody(TelemetryEvent.routeChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), MetricNames.javaLabels("noc", "trace"), ts));
        assertTrue(body.contains("\"job\":\"pingui\""));
        assertTrue(body.contains("\"site\":\"noc\""));
        assertTrue(body.contains("\"host\":\"8.8.8.8\""));
        assertTrue(body.contains("route_change"));
        assertTrue(body.contains(LokiPushSink.nanosString(ts)));
        sink.close();
    }

    @Test
    void mockServerReceivesTwoPushPostsAndIgnoresSamples() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        AtomicReference<String> firstBody = new AtomicReference<>();
        AtomicReference<String> secondBody = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(LokiPushSink.PUSH_PATH, exchange -> {
            path.set(exchange.getRequestURI().getPath());
            String raw = new String(exchange.getRequestBody().readAllBytes());
            int n = posts.incrementAndGet();
            if (n == 1) {
                firstBody.set(raw);
            } else if (n == 2) {
                secondBody.set(raw);
            }
            exchange.sendResponseHeaders(204, -1);
            try (OutputStream out = exchange.getResponseBody()) {
                // empty 204
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Instant ts = Instant.parse("2026-07-14T06:20:00Z");
            LokiPushSink sink = new LokiPushSink(
                    "http://127.0.0.1:" + port, "lab", HttpClient.newHttpClient(), Duration.ofSeconds(5));
            sink.onEvent(TelemetryEvent.routeChange(
                    "8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), MetricNames.javaLabels("lab", "trace"), ts));
            sink.onEvent(TelemetryEvent.probeError("8.8.8.8", "timeout", MetricNames.javaLabels("lab", "trace"), ts));
            sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 1.0, MetricNames.javaLabels("lab", "trace"), ts));
            sink.close();
            assertEquals(LokiPushSink.PUSH_PATH, path.get());
            assertEquals(2, posts.get());
            assertTrue(firstBody.get().contains("route_change"));
            assertTrue(firstBody.get().contains("\"job\":\"pingui\""));
            assertTrue(secondBody.get().contains("probe_error"));
            assertTrue(secondBody.get().contains("\"site\":\"lab\""));
        } finally {
            server.stop(0);
        }
    }
}
