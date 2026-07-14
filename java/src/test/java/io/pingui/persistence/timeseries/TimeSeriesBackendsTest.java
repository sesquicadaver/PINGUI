package io.pingui.persistence.timeseries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.CliTimeSeriesOverrides;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TimeSeriesBackendsTest {
    @Test
    void createDisabledReturnsNull() {
        assertNull(TimeSeriesBackends.create(null));
        assertNull(TimeSeriesBackends.create(CliTimeSeriesOverrides.none()));
        assertNull(TimeSeriesBackends.create(new CliTimeSeriesOverrides(
                Optional.of("none"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty())));
    }

    @Test
    void createUnknownBackendThrows() {
        assertThrows(
                TimeSeriesConfigException.class,
                () -> TimeSeriesBackends.create(new CliTimeSeriesOverrides(
                        Optional.of("prometheus"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())));
    }

    @Test
    void createInfluxRequiresConfig() {
        TimeSeriesConfigException ex = assertThrows(
                TimeSeriesConfigException.class,
                () -> TimeSeriesBackends.create(new CliTimeSeriesOverrides(
                        Optional.of("influx"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())));
        assertTrue(ex.getMessage().contains("InfluxDB"));
    }

    @Test
    void createTimescaleRequiresDsn() {
        TimeSeriesConfigException ex = assertThrows(
                TimeSeriesConfigException.class,
                () -> TimeSeriesBackends.create(new CliTimeSeriesOverrides(
                        Optional.of("timescale"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())));
        assertTrue(ex.getMessage().contains("Timescale"));
    }

    @Test
    void influxFormatsLineProtocol() {
        Instant ts = Instant.parse("2026-06-26T12:00:00Z");
        String ping = InfluxTimeSeriesBackend.formatPingLine(new PingSample("host a", 1, "8.8.8.8", 12.5, ts));
        assertTrue(ping.startsWith("pingui_rtt,target=host\\ a,hop_ip=8.8.8.8 hop=1i,rtt_ms=12.5 "));
        String route = InfluxTimeSeriesBackend.formatRouteLine(
                new RouteEvent("host", List.of("10.0.0.1", "8.8.8.8"), true, ts));
        assertTrue(route.contains("route_changed=1i"));
        assertTrue(route.contains("route_ips=\"10.0.0.1,8.8.8.8\""));
    }

    @Test
    void influxHttpWritePostsLineProtocol() throws Exception {
        java.util.List<String> bodies = new java.util.ArrayList<>();
        AtomicReference<String> auth = new AtomicReference<>();
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/write", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        try {
            InfluxTimeSeriesBackend backend =
                    new InfluxTimeSeriesBackend("http://127.0.0.1:" + port, "secret-token", "org", "bucket");
            Instant ts = Instant.parse("2026-06-26T12:00:00Z");
            backend.writePingSamples(List.of(new PingSample("h", 1, "1.1.1.1", 5.0, ts)));
            backend.writeRouteEvent(new RouteEvent("h", List.of("1.1.1.1"), false, ts));
            backend.close();
            assertEquals("Token secret-token", auth.get());
            assertEquals(2, bodies.size());
            assertTrue(bodies.get(0).contains("pingui_rtt"));
            assertTrue(bodies.get(1).contains("pingui_route"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void timescaleNormalizesJdbcUrl() {
        assertEquals(
                "jdbc:postgresql://localhost/db",
                TimescaleTimeSeriesBackend.normalizeJdbcUrl("postgresql://localhost/db"));
        assertEquals(
                "jdbc:postgresql://localhost/db",
                TimescaleTimeSeriesBackend.normalizeJdbcUrl("postgres://localhost/db"));
        assertEquals("jdbc:postgresql://x", TimescaleTimeSeriesBackend.normalizeJdbcUrl("jdbc:postgresql://x"));
    }

    @Test
    void timescaleJsonArrayEscapes() {
        assertEquals("[\"a\",\"b\\\"c\"]", TimescaleTimeSeriesBackend.toJsonArray(List.of("a", "b\"c")));
    }
}
