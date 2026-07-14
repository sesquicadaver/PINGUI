package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import org.junit.jupiter.api.Test;

class SinkConfigTest {
    @Test
    void defaultsAreEventsOnly() {
        assertTrue(SinkConfig.defaults().eventsOnly());
        assertTrue(SinkConfig.forRemoteLogSinks(true).eventsOnly());
        assertFalse(SinkConfig.forRemoteLogSinks(false).eventsOnly());
        assertFalse(SinkConfig.defaults().withEventsOnly(false).eventsOnly());
        assertEquals(SinkConfig.defaults(), SinkConfig.require(null));
    }

    @Test
    void remoteLogIdsCoverSyslogGelfLokiOtlp() {
        assertTrue(SinkConfig.isRemoteLogSink(SyslogSink.ID));
        assertTrue(SinkConfig.isRemoteLogSink(GelfSink.ID));
        assertTrue(SinkConfig.isRemoteLogSink(LokiPushSink.ID));
        assertTrue(SinkConfig.isRemoteLogSink(OtlpHttpTelemetrySink.ID));
        assertFalse(SinkConfig.isRemoteLogSink("jsonl"));
        assertFalse(SinkConfig.isRemoteLogSink(null));
        assertEquals(4, SinkConfig.REMOTE_LOG_IDS.size());
    }

    @Test
    void remoteSinksHonorSharedConfig() {
        SinkConfig allowSamples = SinkConfig.forRemoteLogSinks(false);
        assertFalse(new SyslogSink("127.0.0.1", 1514, false, allowSamples).eventsOnly());
        assertFalse(new GelfSink("127.0.0.1", 12201, GelfSink.Transport.TCP, allowSamples).eventsOnly());
        assertFalse(new LokiPushSink("http://127.0.0.1:3100", "lab", allowSamples).eventsOnly());
        assertTrue(new SyslogSink("127.0.0.1", 1514).eventsOnly());
        assertTrue(new GelfSink("127.0.0.1", 12201).eventsOnly());
        assertTrue(new LokiPushSink("http://127.0.0.1:3100", "lab").eventsOnly());
    }

    @Test
    void registrySkipsSamplesWhenEventsOnly() {
        SinkRegistry registry = new SinkRegistry();
        AtomicInteger samples = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        registry.register(new TelemetrySink() {
            @Override
            public String id() {
                return SyslogSink.ID;
            }

            @Override
            public boolean eventsOnly() {
                return SinkConfig.defaults().eventsOnly();
            }

            @Override
            public void onSample(MetricSample sample) {
                samples.incrementAndGet();
            }

            @Override
            public void onEvent(TelemetryEvent event) {
                events.incrementAndGet();
            }
        });
        Instant ts = Instant.parse("2026-07-14T06:40:00Z");
        registry.emitSample(MetricSample.rttMs("8.8.8.8", 1, 1.0, MetricNames.javaLabels("lab", "trace"), ts));
        registry.emitEvent(TelemetryEvent.probeError("8.8.8.8", "x", MetricNames.javaLabels("lab", "trace"), ts));
        assertEquals(0, samples.get());
        assertEquals(1, events.get());
    }

    @Test
    void lokiPostsSampleWhenEventsOnlyFalse() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(LokiPushSink.PUSH_PATH, exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            posts.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            try (OutputStream out = exchange.getResponseBody()) {
                // empty
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Instant ts = Instant.parse("2026-07-14T06:41:00Z");
            LokiPushSink sink = new LokiPushSink(
                    "http://127.0.0.1:" + port,
                    "lab",
                    HttpClient.newHttpClient(),
                    Duration.ofSeconds(5),
                    SinkConfig.forRemoteLogSinks(false));
            sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 12.5, MetricNames.javaLabels("lab", "trace"), ts));
            sink.close();
            assertEquals(1, posts.get());
            assertTrue(body.get().contains("pingui_rtt_ms"));
            assertTrue(body.get().contains("\"job\":\"pingui\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void syslogFormatsSampleWhenEventsOnlyFalse() {
        Instant ts = Instant.parse("2026-07-14T06:42:00Z");
        SyslogSink sink = new SyslogSink(
                "127.0.0.1",
                1514,
                false,
                "pingui",
                "testhost",
                SocketFactory.getDefault(),
                Clock.fixed(ts, ZoneOffset.UTC),
                SinkConfig.forRemoteLogSinks(false));
        String line = sink.formatSampleMessage(
                MetricSample.rttMs("8.8.8.8", 1, 9.0, MetricNames.javaLabels("lab", "trace"), ts));
        assertTrue(line.contains("pingui_rtt_ms"));
        assertTrue(line.contains("\"kind\":\"sample\""));
        sink.close();
    }

    @Test
    void gelfFormatsSampleWhenEventsOnlyFalse() {
        Instant ts = Instant.parse("2026-07-14T06:43:00Z");
        GelfSink sink = new GelfSink(
                "127.0.0.1",
                12201,
                GelfSink.Transport.TCP,
                "testhost",
                SocketFactory.getDefault(),
                () -> {
                    throw new UnsupportedOperationException();
                },
                Clock.fixed(ts, ZoneOffset.UTC),
                SinkConfig.forRemoteLogSinks(false));
        String json = sink.formatSamplePayload(
                MetricSample.rttMs("8.8.8.8", 2, 3.5, MetricNames.javaLabels("lab", "trace"), ts));
        assertTrue(json.contains("\"short_message\":\"pingui_rtt_ms\""));
        assertTrue(json.contains("\"_hop\":2"));
        assertTrue(json.contains("\"_value\":3.5"));
        sink.close();
    }
}
