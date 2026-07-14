package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class OtlpHttpTelemetrySinkTest {
    @Test
    void eventsOnlyAndPaths() {
        OtlpHttpTelemetrySink sink = new OtlpHttpTelemetrySink("http://127.0.0.1:4318/");
        assertEquals(OtlpHttpTelemetrySink.ID, sink.id());
        assertTrue(sink.eventsOnly());
        assertEquals("http://127.0.0.1:4318/v1/logs", sink.logsUri().toString());
        assertEquals("http://127.0.0.1:4318/v1/metrics", sink.metricsUri().toString());
        sink.close();
    }

    @Test
    void formatLogsBodyUsesSharedEventJson() {
        Instant ts = Instant.parse("2026-07-14T10:48:00Z");
        OtlpHttpTelemetrySink sink =
                new OtlpHttpTelemetrySink("http://127.0.0.1:4318", "pingui-lab", SinkConfig.defaults());
        TelemetryEvent event = TelemetryEvent.routeChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), MetricNames.javaLabels("noc", "trace"), ts);
        String body = sink.formatLogsBody(event);
        assertTrue(body.contains("\"resourceLogs\""));
        assertTrue(body.contains("\"service.name\""));
        assertTrue(body.contains("pingui-lab"));
        assertTrue(body.contains("\"severityNumber\":" + OtlpHttpTelemetrySink.SEVERITY_INFO));
        assertTrue(body.contains(event.toJson().replace("\"", "\\\"")) || body.contains("route_change"));
        assertTrue(body.contains("\"timeUnixNano\":\"1752490080000000000\"")
                || body.contains(OtlpHttpTelemetrySink.nanosString(ts)));
        sink.close();
    }

    @Test
    void formatMetricsBodyIsGauge() {
        Instant ts = Instant.parse("2026-07-14T10:48:00Z");
        OtlpHttpTelemetrySink sink =
                new OtlpHttpTelemetrySink("http://127.0.0.1:4318", "pingui", SinkConfig.forRemoteLogSinks(false));
        MetricSample sample = MetricSample.rttMs("8.8.8.8", 2, 12.5, MetricNames.javaLabels("noc", "trace"), ts);
        String body = sink.formatMetricsBody(sample);
        assertTrue(body.contains("\"resourceMetrics\""));
        assertTrue(body.contains("\"pingui_rtt_ms\""));
        assertTrue(body.contains("\"asDouble\":12.5"));
        assertTrue(body.contains("\"hop\""));
        sink.close();
    }

    @Test
    void mockHttpReceivesLogsNotSamplesWhenEventsOnly() throws Exception {
        List<Captured> captured = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/logs", exchange -> capture(exchange, captured));
        server.createContext("/v1/metrics", exchange -> capture(exchange, captured));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Instant ts = Instant.parse("2026-07-14T10:50:00Z");
            try (OtlpHttpTelemetrySink sink = new OtlpHttpTelemetrySink(
                    base,
                    "pingui",
                    java.net.http.HttpClient.newHttpClient(),
                    Duration.ofSeconds(2),
                    SinkConfig.defaults())) {
                sink.onEvent(
                        TelemetryEvent.probeError("8.8.8.8", "timeout", MetricNames.javaLabels("noc", "trace"), ts));
                sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 1.0, MetricNames.javaLabels("noc", "trace"), ts));
            }
            await(() -> captured.size() >= 1, 2000);
            assertEquals(1, captured.size());
            assertEquals("/v1/logs", captured.get(0).path());
            assertTrue(captured.get(0).body().contains("probe_error"));
            assertTrue(captured.get(0).body().contains("\"severityNumber\":" + OtlpHttpTelemetrySink.SEVERITY_WARN));
            assertFalse(captured.stream().anyMatch(c -> "/v1/metrics".equals(c.path())));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mockHttpReceivesMetricsWhenEventsOnlyFalse() throws Exception {
        List<Captured> captured = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/logs", exchange -> capture(exchange, captured));
        server.createContext("/v1/metrics", exchange -> capture(exchange, captured));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Instant ts = Instant.parse("2026-07-14T10:51:00Z");
            try (OtlpHttpTelemetrySink sink = new OtlpHttpTelemetrySink(
                    base,
                    "pingui",
                    java.net.http.HttpClient.newHttpClient(),
                    Duration.ofSeconds(2),
                    SinkConfig.forRemoteLogSinks(false))) {
                sink.onSample(MetricSample.rttMs("1.1.1.1", 1, 3.0, MetricNames.javaLabels("lab", "mtr"), ts));
            }
            await(() -> captured.size() >= 1, 2000);
            assertEquals(1, captured.size());
            assertEquals("/v1/metrics", captured.get(0).path());
            assertTrue(captured.get(0).body().contains("pingui_rtt_ms"));
        } finally {
            server.stop(0);
        }
    }

    private static void capture(com.sun.net.httpserver.HttpExchange exchange, List<Captured> out) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        out.add(new Captured(exchange.getRequestURI().getPath(), new String(bytes, StandardCharsets.UTF_8)));
        byte[] ok = new byte[0];
        exchange.sendResponseHeaders(200, ok.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(ok);
        }
    }

    private static void await(Check check, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(check.ok(), "condition not met");
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private record Captured(String path, String body) {}
}
