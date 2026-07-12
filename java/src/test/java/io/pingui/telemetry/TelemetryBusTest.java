package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TelemetryBusTest {
    private static final Instant TS = Instant.parse("2026-07-12T14:30:00Z");

    @Test
    void offersFlushToRegistry() throws Exception {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink sink = new RecordingSink("rec", false);
        registry.register(sink);
        try (TelemetryBus bus = new TelemetryBus(registry, 16, DropPolicy.DROP_OLDEST, 8, Duration.ofMillis(10))) {
            assertTrue(bus.offerSample(sample(1)));
            assertTrue(bus.offerEvent(event("a")));
            assertTrue(await(() -> sink.samples.size() == 1 && sink.events.size() == 1, 2_000));
        }
        assertEquals(1, sink.samples.size());
        assertEquals(1, sink.events.size());
    }

    @Test
    void dropOldestDiscardsHeadAndAcceptsNew() throws Exception {
        SinkRegistry registry = new SinkRegistry();
        BlockingSink sink = new BlockingSink();
        registry.register(sink);
        try (TelemetryBus bus = new TelemetryBus(registry, 2, DropPolicy.DROP_OLDEST, 1, Duration.ofMillis(5))) {
            // Fill queue while worker is blocked on first delivered item.
            assertTrue(bus.offerSample(sample(1)));
            assertTrue(await(() -> sink.entered.get() >= 1, 2_000));
            assertTrue(bus.offerSample(sample(2)));
            assertTrue(bus.offerSample(sample(3)));
            // capacity 2: one in-flight via sink, two queued → third offer drops oldest queued
            assertTrue(bus.offerSample(sample(4)));
            assertTrue(bus.droppedCount() >= 1);
            sink.release.countDown();
            assertTrue(await(() -> sink.seen.contains(4), 2_000));
            assertTrue(bus.droppedCount() >= 1);
            assertFalse(sink.seen.contains(2)); // oldest queued was dropped when 4 arrived
        } finally {
            sink.release.countDown();
        }
    }

    @Test
    void dropNewestRejectsWhenFull() throws Exception {
        SinkRegistry registry = new SinkRegistry();
        BlockingSink sink = new BlockingSink();
        registry.register(sink);
        try (TelemetryBus bus = new TelemetryBus(registry, 1, DropPolicy.DROP_NEWEST, 1, Duration.ofMillis(5))) {
            assertTrue(bus.offerSample(sample(1)));
            assertTrue(await(() -> sink.entered.get() >= 1, 2_000));
            assertTrue(bus.offerSample(sample(2)));
            assertFalse(bus.offerSample(sample(3)));
            assertTrue(bus.droppedCount() >= 1);
            sink.release.countDown();
            assertTrue(await(() -> sink.seen.contains(2), 2_000));
            assertFalse(sink.seen.contains(3));
        } finally {
            sink.release.countDown();
        }
    }

    @Test
    void closeDrainsRemaining() throws Exception {
        SinkRegistry registry = new SinkRegistry();
        RecordingSink sink = new RecordingSink("rec", false);
        registry.register(sink);
        TelemetryBus bus = new TelemetryBus(registry, 32, DropPolicy.DROP_OLDEST, 4, Duration.ofHours(1));
        assertTrue(bus.offerSample(sample(7)));
        assertTrue(bus.offerEvent(event("z")));
        bus.close();
        assertEquals(1, sink.samples.size());
        assertEquals(1, sink.events.size());
    }

    @Test
    void rejectsInvalidConfig() {
        SinkRegistry registry = new SinkRegistry();
        assertThrows(
                IllegalArgumentException.class,
                () -> new TelemetryBus(registry, 0, DropPolicy.DROP_OLDEST, 1, Duration.ofMillis(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TelemetryBus(registry, 1, DropPolicy.DROP_OLDEST, 0, Duration.ofMillis(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TelemetryBus(registry, 1, DropPolicy.DROP_OLDEST, 1, Duration.ZERO));
    }

    private static MetricSample sample(int hop) {
        return MetricSample.rttMs("8.8.8.8", hop, hop * 1.0, Map.of("profile", "default"), TS);
    }

    private static TelemetryEvent event(String message) {
        return TelemetryEvent.probeError("8.8.8.8", message, Map.of("profile", "default"), TS);
    }

    private static boolean await(Check check, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return true;
            }
            Thread.sleep(5);
        }
        return check.ok();
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private static final class RecordingSink implements TelemetrySink {
        private final String id;
        private final boolean eventsOnly;
        private final List<MetricSample> samples = new CopyOnWriteArrayList<>();
        private final List<TelemetryEvent> events = new CopyOnWriteArrayList<>();

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

    /** Blocks inside onSample until released — backs up the queue for drop tests. */
    private static final class BlockingSink implements TelemetrySink {
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger entered = new AtomicInteger();
        private final List<Integer> seen = new CopyOnWriteArrayList<>();

        @Override
        public String id() {
            return "block";
        }

        @Override
        public void onSample(MetricSample sample) {
            entered.incrementAndGet();
            seen.add(sample.hop());
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void onEvent(TelemetryEvent event) {}
    }
}
