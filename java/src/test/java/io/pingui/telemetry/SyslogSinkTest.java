package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

class SyslogSinkTest {
    @Test
    void eventsOnlyAndId() {
        SyslogSink sink = new SyslogSink("127.0.0.1", 1514);
        assertEquals(SyslogSink.ID, sink.id());
        assertTrue(sink.eventsOnly());
        sink.close();
    }

    @Test
    void formatMessageUsesRfc5424AndJsonMsg() {
        Instant ts = Instant.parse("2026-07-14T05:51:00Z");
        Clock clock = Clock.fixed(ts, ZoneOffset.UTC);
        SyslogSink sink =
                new SyslogSink("127.0.0.1", 1514, false, "pingui", "testhost", SocketFactory.getDefault(), clock);
        TelemetryEvent event = TelemetryEvent.routeChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), MetricNames.javaLabels("noc", "trace"), ts);
        String line = sink.formatMessage(event);
        int expectedPri = SyslogSink.FACILITY_LOCAL0 * 8 + SyslogSink.SEVERITY_NOTICE;
        assertTrue(line.startsWith("<" + expectedPri + ">1 "));
        assertTrue(line.contains(" testhost pingui - - - "));
        assertTrue(line.contains("\"kind\":\"event\""));
        assertTrue(line.contains("route_change"));
        assertFalse(line.contains("\n"));
        sink.close();
    }

    @Test
    void probeErrorUsesWarningSeverity() {
        Instant ts = Instant.parse("2026-07-14T05:51:00Z");
        SyslogSink sink = new SyslogSink(
                "127.0.0.1",
                1514,
                false,
                "pingui",
                "testhost",
                SocketFactory.getDefault(),
                Clock.fixed(ts, ZoneOffset.UTC));
        String line = sink.formatMessage(
                TelemetryEvent.probeError("1.1.1.1", "timeout", MetricNames.javaLabels("default", "trace"), ts));
        int expectedPri = SyslogSink.FACILITY_LOCAL0 * 8 + SyslogSink.SEVERITY_WARNING;
        assertTrue(line.startsWith("<" + expectedPri + ">1 "));
        sink.close();
    }

    @Test
    void mockTcpServerReceivesFramedEvents() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            AtomicReference<List<String>> linesRef = new AtomicReference<>(List.of());
            AtomicReference<Exception> error = new AtomicReference<>();
            Thread acceptor = new Thread(
                    () -> {
                        try (Socket client = server.accept();
                                BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                            List<String> lines = new ArrayList<>();
                            String line;
                            while (lines.size() < 2 && (line = reader.readLine()) != null) {
                                lines.add(line);
                            }
                            linesRef.set(List.copyOf(lines));
                        } catch (Exception ex) {
                            error.set(ex);
                        }
                    },
                    "syslog-mock");
            acceptor.setDaemon(true);
            acceptor.start();

            Instant ts = Instant.parse("2026-07-14T06:00:00Z");
            try (SyslogSink sink = new SyslogSink(
                    "127.0.0.1",
                    port,
                    false,
                    "pingui",
                    "testhost",
                    SocketFactory.getDefault(),
                    Clock.fixed(ts, ZoneOffset.UTC))) {
                sink.onEvent(TelemetryEvent.routeChange(
                        "8.8.8.8",
                        List.of("10.0.0.1"),
                        List.of("8.8.8.8"),
                        MetricNames.javaLabels("noc", "trace"),
                        ts));
                sink.onEvent(
                        TelemetryEvent.probeError("8.8.8.8", "timeout", MetricNames.javaLabels("noc", "trace"), ts));
                // samples must not be transmitted (events_only)
                sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 1.0, MetricNames.javaLabels("noc", "trace"), ts));
            }
            acceptor.join(5000);
            if (error.get() != null) {
                throw error.get();
            }
            List<String> lines = linesRef.get();
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("route_change"));
            assertTrue(lines.get(1).contains("probe_error"));
            assertTrue(lines.get(0).startsWith("<"));
            assertTrue(lines.get(1).contains("\"kind\":\"event\""));
        }
    }
}
