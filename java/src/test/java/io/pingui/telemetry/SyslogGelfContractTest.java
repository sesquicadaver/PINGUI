package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import org.junit.jupiter.api.Test;

/**
 * Contract tests: mock syslog TCP + mock GELF TCP sharing {@link TelemetryLogFieldFixture} (P16-072).
 */
class SyslogGelfContractTest {

    @Test
    void sharedFixtureJsonIsCanonicalForBothSinkFormatters() {
        TelemetryEvent route = TelemetryLogFieldFixture.routeChange();
        TelemetryEvent error = TelemetryLogFieldFixture.probeError();
        TelemetryLogFieldFixture.assertSharedEventFields(route.toJson());
        TelemetryLogFieldFixture.assertSharedEventFields(error.toJson());

        Clock clock = Clock.fixed(TelemetryLogFieldFixture.TS, ZoneOffset.UTC);
        try (SyslogSink syslog = new SyslogSink(
                        "127.0.0.1", 1514, false, "pingui", "testhost", SocketFactory.getDefault(), clock);
                GelfSink gelf = new GelfSink(
                        "127.0.0.1",
                        12201,
                        GelfSink.Transport.TCP,
                        "testhost",
                        SocketFactory.getDefault(),
                        () -> {
                            throw new UnsupportedOperationException();
                        },
                        clock)) {
            String syslogRouteMsg = TelemetryLogFieldFixture.syslogMsgJson(syslog.formatMessage(route));
            assertEquals(route.toJson(), syslogRouteMsg);
            TelemetryLogFieldFixture.assertSharedEventFields(syslogRouteMsg);
            TelemetryLogFieldFixture.assertGelfSharesEventFields(gelf.formatPayload(route), route);

            String syslogErrorMsg = TelemetryLogFieldFixture.syslogMsgJson(syslog.formatMessage(error));
            assertEquals(error.toJson(), syslogErrorMsg);
            TelemetryLogFieldFixture.assertSharedEventFields(syslogErrorMsg);
            TelemetryLogFieldFixture.assertGelfSharesEventFields(gelf.formatPayload(error), error);
        }
    }

    @Test
    void mockSyslogTcpDeliversSharedFieldEventsOnly() throws Exception {
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
                    "syslog-contract-mock");
            acceptor.setDaemon(true);
            acceptor.start();

            Clock clock = Clock.fixed(TelemetryLogFieldFixture.TS, ZoneOffset.UTC);
            try (SyslogSink sink =
                    new SyslogSink("127.0.0.1", port, false, "pingui", "testhost", SocketFactory.getDefault(), clock)) {
                sink.onEvent(TelemetryLogFieldFixture.routeChange());
                sink.onEvent(TelemetryLogFieldFixture.probeError());
                sink.onSample(TelemetryLogFieldFixture.droppedSample());
            }
            acceptor.join(5000);
            if (error.get() != null) {
                throw error.get();
            }
            List<String> lines = linesRef.get();
            assertEquals(2, lines.size());
            String routeMsg = TelemetryLogFieldFixture.syslogMsgJson(lines.get(0));
            String errorMsg = TelemetryLogFieldFixture.syslogMsgJson(lines.get(1));
            TelemetryLogFieldFixture.assertSharedEventFields(routeMsg);
            TelemetryLogFieldFixture.assertSharedEventFields(errorMsg);
            assertEquals(TelemetryLogFieldFixture.routeChange().toJson(), routeMsg);
            assertEquals(TelemetryLogFieldFixture.probeError().toJson(), errorMsg);
            assertFalse(lines.get(0).contains(MetricNames.RTT_MS));
            assertFalse(lines.get(1).contains(MetricNames.RTT_MS));
        }
    }

    @Test
    void mockGelfTcpDeliversSharedFieldEventsOnly() throws Exception {
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
                    "gelf-contract-mock");
            acceptor.setDaemon(true);
            acceptor.start();

            Clock clock = Clock.fixed(TelemetryLogFieldFixture.TS, ZoneOffset.UTC);
            try (GelfSink sink = new GelfSink(
                    "127.0.0.1",
                    port,
                    GelfSink.Transport.TCP,
                    "testhost",
                    SocketFactory.getDefault(),
                    () -> {
                        throw new UnsupportedOperationException();
                    },
                    clock)) {
                sink.onEvent(TelemetryLogFieldFixture.routeChange());
                sink.onEvent(TelemetryLogFieldFixture.probeError());
                sink.onSample(TelemetryLogFieldFixture.droppedSample());
            }
            acceptor.join(5000);
            if (error.get() != null) {
                throw error.get();
            }
            List<String> payloads = payloadsRef.get();
            assertEquals(2, payloads.size());
            TelemetryLogFieldFixture.assertGelfSharesEventFields(
                    payloads.get(0), TelemetryLogFieldFixture.routeChange());
            TelemetryLogFieldFixture.assertGelfSharesEventFields(
                    payloads.get(1), TelemetryLogFieldFixture.probeError());
            assertTrue(payloads.stream().noneMatch(p -> p.contains("\"_metric\"")));
        }
    }
}
