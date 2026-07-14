package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import org.junit.jupiter.api.Test;

class GelfSinkTest {
    @Test
    void eventsOnlyAndId() {
        GelfSink sink = new GelfSink("127.0.0.1", 12201);
        assertEquals(GelfSink.ID, sink.id());
        assertTrue(sink.eventsOnly());
        sink.close();
    }

    @Test
    void formatPayloadIsGelf11WithUnderscoreFields() {
        Instant ts = Instant.parse("2026-07-14T06:00:00Z");
        GelfSink sink = new GelfSink(
                "127.0.0.1",
                12201,
                GelfSink.Transport.TCP,
                "testhost",
                SocketFactory.getDefault(),
                () -> {
                    throw new UnsupportedOperationException();
                },
                Clock.fixed(ts, ZoneOffset.UTC));
        String json = sink.formatPayload(TelemetryEvent.routeChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), MetricNames.javaLabels("noc", "trace"), ts));
        assertTrue(json.contains("\"version\":\"1.1\""));
        assertTrue(json.contains("\"host\":\"8.8.8.8\""));
        assertTrue(json.contains("\"short_message\":\"route_change\""));
        assertTrue(json.contains("\"_profile\":\"noc\""));
        assertTrue(json.contains("\"_probe_mode\":\"trace\""));
        assertTrue(json.contains("\"_old_ips\":[\"10.0.0.1\"]"));
        assertTrue(json.contains("\"_new_ips\":[\"8.8.8.8\"]"));
        assertTrue(json.contains("\"level\":" + SyslogSink.SEVERITY_NOTICE));
        sink.close();
    }

    @Test
    void probeErrorUsesWarningLevel() {
        Instant ts = Instant.parse("2026-07-14T06:00:00Z");
        GelfSink sink = new GelfSink(
                "127.0.0.1",
                12201,
                GelfSink.Transport.TCP,
                "testhost",
                SocketFactory.getDefault(),
                () -> {
                    throw new UnsupportedOperationException();
                },
                Clock.fixed(ts, ZoneOffset.UTC));
        String json = sink.formatPayload(
                TelemetryEvent.probeError("1.1.1.1", "timeout", MetricNames.javaLabels("default", "trace"), ts));
        assertTrue(json.contains("\"short_message\":\"probe_error\""));
        assertTrue(json.contains("\"full_message\":\"timeout\""));
        assertTrue(json.contains("\"level\":" + SyslogSink.SEVERITY_WARNING));
        sink.close();
    }

    @Test
    void mockTcpServerReceivesNullTerminatedFrames() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            AtomicReference<List<String>> payloadsRef = new AtomicReference<>(List.of());
            AtomicReference<Exception> error = new AtomicReference<>();
            Thread acceptor = new Thread(
                    () -> {
                        try (Socket client = server.accept();
                                InputStream in = client.getInputStream()) {
                            List<String> payloads = new ArrayList<>();
                            while (payloads.size() < 2) {
                                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                                int b;
                                while ((b = in.read()) != -1 && b != 0) {
                                    buf.write(b);
                                }
                                if (b == -1 && buf.size() == 0) {
                                    break;
                                }
                                payloads.add(buf.toString(StandardCharsets.UTF_8));
                            }
                            payloadsRef.set(List.copyOf(payloads));
                        } catch (Exception ex) {
                            error.set(ex);
                        }
                    },
                    "gelf-mock");
            acceptor.setDaemon(true);
            acceptor.start();

            Instant ts = Instant.parse("2026-07-14T06:10:00Z");
            try (GelfSink sink = new GelfSink(
                    "127.0.0.1",
                    port,
                    GelfSink.Transport.TCP,
                    "testhost",
                    SocketFactory.getDefault(),
                    () -> {
                        throw new UnsupportedOperationException();
                    },
                    Clock.fixed(ts, ZoneOffset.UTC))) {
                sink.onEvent(TelemetryEvent.routeChange(
                        "8.8.8.8",
                        List.of("10.0.0.1"),
                        List.of("8.8.8.8"),
                        MetricNames.javaLabels("noc", "trace"),
                        ts));
                sink.onEvent(
                        TelemetryEvent.probeError("8.8.8.8", "timeout", MetricNames.javaLabels("noc", "trace"), ts));
                sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 1.0, MetricNames.javaLabels("noc", "trace"), ts));
            }
            acceptor.join(5000);
            if (error.get() != null) {
                throw error.get();
            }
            List<String> payloads = payloadsRef.get();
            assertEquals(2, payloads.size());
            assertTrue(payloads.get(0).contains("route_change"));
            assertTrue(payloads.get(1).contains("probe_error"));
            assertTrue(payloads.get(0).contains("\"version\":\"1.1\""));
        }
    }
}
