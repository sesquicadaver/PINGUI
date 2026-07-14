package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AggregateTelemetryJobTest {
    private static final Instant T0 = Instant.parse("2026-07-14T06:00:00Z");

    @Test
    void disabledIsNoOp() {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink sink = new RecordingSink();
        registry.register(sink);
        AggregateTelemetryJob job = AggregateTelemetryJob.disabled(registry);
        assertFalse(job.logAggregates());
        job.accept(rtt("8.8.8.8", 1, 10.0, T0));
        assertEquals(0, job.flushAll());
        assertTrue(sink.events.isEmpty());
    }

    @Test
    void flushDueEmitsAvgAndMaxPerHop() {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink sink = new RecordingSink("log", true);
        registry.register(sink);
        Clock clock = Clock.fixed(T0.plus(Duration.ofMinutes(5)), ZoneOffset.UTC);
        AggregateTelemetryJob job = new AggregateTelemetryJob(registry, true, Duration.ofMinutes(5), clock);

        job.accept(rtt("8.8.8.8", 1, 10.0, T0.plusSeconds(10)));
        job.accept(rtt("8.8.8.8", 1, 30.0, T0.plusSeconds(20)));
        job.accept(rtt("8.8.8.8", 2, 5.0, T0.plusSeconds(30)));
        job.accept(MetricSample.rttMs(
                "8.8.8.8", 1, 99.0, MetricNames.javaLabels("lab", "trace"), T0.plus(Duration.ofMinutes(6))));

        assertEquals(0, job.flushDue(T0.plus(Duration.ofMinutes(4))));
        assertEquals(2, job.flushDue(T0.plus(Duration.ofMinutes(5))));

        assertEquals(2, sink.events.size());
        assertTrue(sink.samples.isEmpty());
        TelemetryEvent hop1 = sink.events.stream()
                .filter(e -> e.message().contains("\"hop\":1"))
                .findFirst()
                .orElseThrow();
        assertEquals(TelemetryEvent.RTT_AGGREGATE, hop1.event());
        assertTrue(hop1.message().contains("\"avg_ms\":20"));
        assertTrue(hop1.message().contains("\"max_ms\":30"));
        assertTrue(hop1.message().contains("\"count\":2"));
        assertTrue(hop1.message().contains("\"window_sec\":300"));
        TelemetryEvent hop2 = sink.events.stream()
                .filter(e -> e.message().contains("\"hop\":2"))
                .findFirst()
                .orElseThrow();
        assertTrue(hop2.message().contains("\"avg_ms\":5"));
        assertTrue(hop2.message().contains("\"max_ms\":5"));
    }

    @Test
    void ignoresNonRttAndHopLess() {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink sink = new RecordingSink();
        registry.register(sink);
        AggregateTelemetryJob job = AggregateTelemetryJob.enabled(registry);
        job.accept(new MetricSample(
                MetricNames.TARGET_REACHABLE, 1.0, "8.8.8.8", null, MetricNames.javaLabels("lab", "trace"), T0));
        job.accept(
                new MetricSample(MetricNames.RTT_MS, 1.0, "8.8.8.8", null, MetricNames.javaLabels("lab", "trace"), T0));
        assertEquals(0, job.flushAll());
        assertTrue(sink.events.isEmpty());
    }

    @Test
    void eventsOnlySinkStillReceivesAggregates() {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink sink = new RecordingSink("syslog", true);
        registry.register(sink);
        AggregateTelemetryJob job = AggregateTelemetryJob.enabled(registry);
        job.accept(rtt("1.1.1.1", 1, 12.0, T0));
        registry.emitSample(rtt("1.1.1.1", 1, 12.0, T0));
        assertEquals(1, job.flushAll());
        assertEquals(1, sink.events.size());
        assertTrue(sink.samples.isEmpty());
    }

    @Test
    void alignWindowStartFloorsToEpochBuckets() {
        long windowMs = Duration.ofMinutes(5).toMillis();
        assertEquals(T0.toEpochMilli(), AggregateTelemetryJob.alignWindowStart(T0.toEpochMilli(), windowMs));
        assertEquals(
                T0.toEpochMilli(),
                AggregateTelemetryJob.alignWindowStart(T0.plusSeconds(299).toEpochMilli(), windowMs));
        assertEquals(
                T0.plus(Duration.ofMinutes(5)).toEpochMilli(),
                AggregateTelemetryJob.alignWindowStart(
                        T0.plus(Duration.ofMinutes(5)).toEpochMilli(), windowMs));
    }

    private static MetricSample rtt(String host, int hop, double ms, Instant ts) {
        return MetricSample.rttMs(host, hop, ms, MetricNames.javaLabels("lab", "trace"), ts);
    }

    private static final class RecordingSink implements TelemetrySink {
        private final String id;
        private final boolean eventsOnly;
        private final List<MetricSample> samples = new ArrayList<>();
        private final List<TelemetryEvent> events = new ArrayList<>();

        private RecordingSink() {
            this("rec", false);
        }

        private RecordingSink(String id, boolean eventsOnly) {
            this.id = id;
            this.eventsOnly = eventsOnly;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean eventsOnly() {
            return eventsOnly;
        }

        @Override
        public void onSample(MetricSample sample) {
            samples.add(sample);
        }

        @Override
        public void onEvent(TelemetryEvent event) {
            events.add(event);
        }
    }
}
