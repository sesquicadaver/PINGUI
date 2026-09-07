package io.pingui.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for the outer DNS-control dispatcher (P35-005). */
class DnsControlDispatcherTest {
    @Test
    void coalescesConcurrentSubmitsForSameHost() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (DnsControlDispatcher dispatcher = new DnsControlDispatcher(8)) {
            assertTrue(dispatcher.submit("a.example", () -> {
                runs.incrementAndGet();
                entered.countDown();
                awaitQuietly(release);
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(dispatcher.submit("a.example", runs::incrementAndGet));
            assertTrue(dispatcher.submit("A.example", runs::incrementAndGet));
            assertEquals(2L, dispatcher.coalescedCount());
            release.countDown();
            waitUntil(() -> dispatcher.pendingSizeForTests() == 0, 2_000);
            assertEquals(1, runs.get());
        }
    }

    @Test
    void rejectsWhenOuterQueueIsFull() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        try (DnsControlDispatcher dispatcher = new DnsControlDispatcher(1)) {
            assertTrue(dispatcher.submit("busy.example", () -> awaitQuietly(hold)));
            waitUntil(
                    () -> dispatcher.opsStats().queued() + dispatcher.opsStats().inFlight() >= 1, 2_000);
            // Worker busy + one queued slot → third distinct host must AbortPolicy-reject.
            assertTrue(dispatcher.submit("queued.example", () -> {}));
            assertFalse(dispatcher.submit("overflow.example", () -> {}));
            assertTrue(dispatcher.rejectedCount() >= 1L);
            DnsOpsStats stats = dispatcher.opsStats();
            assertEquals(1, stats.queueCapacity());
            assertTrue(stats.hasPressure());
            hold.countDown();
        }
    }

    @Test
    void opsStatsExposeCapacityAndPending() {
        try (DnsControlDispatcher dispatcher = DnsControlDispatcher.createDefault()) {
            DnsOpsStats stats = dispatcher.opsStats();
            assertEquals(DnsControlDispatcher.DEFAULT_QUEUE_CAPACITY, stats.queueCapacity());
            assertEquals(0, stats.queued());
            assertEquals(0, stats.inFlight());
            assertEquals(0L, stats.coalescedCount());
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(java.util.concurrent.Callable<Boolean> condition, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(Boolean.TRUE.equals(condition.call()), "condition not met before timeout");
    }
}
