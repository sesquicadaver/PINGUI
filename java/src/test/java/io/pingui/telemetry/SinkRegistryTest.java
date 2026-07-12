package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SinkRegistryTest {
    private static final Instant TS = Instant.parse("2026-07-12T14:00:00Z");

    @Test
    void emptyRegistryIsNoOp() {
        SinkRegistry registry = new SinkRegistry();
        registry.emitSample(sample());
        registry.emitEvent(event());
        assertEquals(0, registry.size());
    }

    @Test
    void registerUnregisterAndFanOut() {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink a = new RecordingSink("a", false);
        RecordingSink b = new RecordingSink("b", false);
        registry.register(a);
        registry.register(b);
        assertEquals(2, registry.size());
        assertTrue(registry.contains("a"));

        registry.emitSample(sample());
        registry.emitEvent(event());
        assertEquals(1, a.samples.size());
        assertEquals(1, b.samples.size());
        assertEquals(1, a.events.size());
        assertEquals(1, b.events.size());

        assertTrue(registry.unregister("a"));
        assertFalse(registry.contains("a"));
        registry.emitEvent(event());
        assertEquals(1, a.events.size());
        assertEquals(2, b.events.size());
    }

    @Test
    void replaceSameIdClosesPrevious() {
        SinkRegistry registry = new SinkRegistry();
        AtomicInteger closed = new AtomicInteger();
        registry.register(new TelemetrySink() {
            @Override
            public String id() {
                return "x";
            }

            @Override
            public void onSample(MetricSample sample) {}

            @Override
            public void onEvent(TelemetryEvent event) {}

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        });
        registry.register(new RecordingSink("x", false));
        assertEquals(1, closed.get());
    }

    @Test
    void replaceSameId() {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink first = new RecordingSink("x", false);
        RecordingSink second = new RecordingSink("x", false);
        registry.register(first);
        registry.register(second);
        assertEquals(1, registry.size());
        registry.emitEvent(event());
        assertEquals(0, first.events.size());
        assertEquals(1, second.events.size());
    }

    @Test
    void eventsOnlySkipsSamples() {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink logSink = new RecordingSink("log", true);
        registry.register(logSink);
        registry.emitSample(sample());
        registry.emitEvent(event());
        assertTrue(logSink.samples.isEmpty());
        assertEquals(1, logSink.events.size());
    }

    @Test
    void eventsOnlyThrowDoesNotStopOthers() {
        SinkRegistry registry = new SinkRegistry();
        AtomicInteger samples = new AtomicInteger();
        registry.register(new TelemetrySink() {
            @Override
            public String id() {
                return "bad-flag";
            }

            @Override
            public boolean eventsOnly() {
                throw new IllegalStateException("flag");
            }

            @Override
            public void onSample(MetricSample sample) {}

            @Override
            public void onEvent(TelemetryEvent event) {}
        });
        registry.register(new TelemetrySink() {
            @Override
            public String id() {
                return "ok";
            }

            @Override
            public void onSample(MetricSample sample) {
                samples.incrementAndGet();
            }

            @Override
            public void onEvent(TelemetryEvent event) {}
        });
        registry.emitSample(sample());
        assertEquals(1, samples.get());
    }

    @Test
    void unregisterAndCloseInvokeClose() {
        SinkRegistry registry = new SinkRegistry();
        AtomicInteger closed = new AtomicInteger();
        TelemetrySink sink = new TelemetrySink() {
            @Override
            public String id() {
                return "res";
            }

            @Override
            public void onSample(MetricSample sample) {}

            @Override
            public void onEvent(TelemetryEvent event) {}

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
        registry.register(sink);
        assertTrue(registry.unregister("res"));
        assertEquals(1, closed.get());

        registry.register(sink);
        registry.close();
        assertEquals(2, closed.get());
        assertEquals(0, registry.size());
    }

    @Test
    void sinkFailureDoesNotStopOthers() {
        SinkRegistry registry = new SinkRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new TelemetrySink() {
            @Override
            public String id() {
                return "boom";
            }

            @Override
            public void onSample(MetricSample sample) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onEvent(TelemetryEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        registry.register(new TelemetrySink() {
            @Override
            public String id() {
                return "ok";
            }

            @Override
            public void onSample(MetricSample sample) {
                calls.incrementAndGet();
            }

            @Override
            public void onEvent(TelemetryEvent event) {
                calls.incrementAndGet();
            }
        });
        registry.emitSample(sample());
        registry.emitEvent(event());
        assertEquals(2, calls.get());
    }

    @Test
    void noopSinkIgnoresPayloads() {
        TelemetrySink.noop().onSample(sample());
        TelemetrySink.noop().onEvent(event());
        assertEquals(NoOpTelemetrySink.ID, TelemetrySink.noop().id());
    }

    @Test
    void rejectsBlankSinkId() {
        SinkRegistry registry = new SinkRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(new RecordingSink("  ", false)));
    }

    private static MetricSample sample() {
        return MetricSample.rttMs("8.8.8.8", 1, 1.0, Map.of("profile", "default"), TS);
    }

    private static TelemetryEvent event() {
        return TelemetryEvent.probeError("8.8.8.8", "timeout", Map.of("profile", "default"), TS);
    }

    private static final class RecordingSink implements TelemetrySink {
        private final String id;
        private final boolean eventsOnly;
        private final List<MetricSample> samples = new ArrayList<>();
        private final List<TelemetryEvent> events = new ArrayList<>();

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
