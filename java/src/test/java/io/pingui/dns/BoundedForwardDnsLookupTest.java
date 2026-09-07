package io.pingui.dns;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedForwardDnsLookupTest {
    @Test
    void cachesSuccessfulLookup() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        InetAddress addr = InetAddress.getByName("1.1.1.1");
        try (BoundedForwardDnsLookup lookup = BoundedForwardDnsLookup.forTests(
                hostname -> {
                    calls.incrementAndGet();
                    return new InetAddress[] {addr};
                },
                Duration.ofSeconds(2),
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC))) {
            assertArrayEquals(new InetAddress[] {addr}, lookup.resolve("cache.example"));
            assertArrayEquals(new InetAddress[] {addr}, lookup.resolve("cache.example"));
            assertEquals(1, calls.get());
            assertEquals(1, lookup.cacheSizeForTests());
        }
    }

    @Test
    void timesOutSlowResolver() {
        try (BoundedForwardDnsLookup lookup = BoundedForwardDnsLookup.forTests(
                hostname -> {
                    Thread.sleep(500);
                    return new InetAddress[] {InetAddress.getByName("8.8.8.8")};
                },
                Duration.ofMillis(50),
                Duration.ofMinutes(1),
                Clock.systemUTC())) {
            assertThrows(SocketTimeoutException.class, () -> lookup.resolve("slow.example"));
        }
    }

    @Test
    void cachesTimeoutFailureBriefly() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try (BoundedForwardDnsLookup lookup = BoundedForwardDnsLookup.forTests(
                hostname -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    // Block until the test finishes — timeout must come from Future.get, not sleep.
                    release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return new InetAddress[] {InetAddress.getByName("8.8.8.8")};
                },
                Duration.ofMillis(50),
                Duration.ofMinutes(1),
                Clock.systemUTC())) {
            assertThrows(SocketTimeoutException.class, () -> lookup.resolve("fail.example"));
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(SocketTimeoutException.class, () -> lookup.resolve("fail.example"));
            assertEquals(1, calls.get(), "negative cache must suppress a second resolver call");
        } finally {
            release.countDown();
        }
    }

    @Test
    void coalescesConcurrentResolvesForSameHost() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        InetAddress addr = InetAddress.getByName("9.9.9.9");
        try (BoundedForwardDnsLookup lookup = BoundedForwardDnsLookup.forTests(
                hostname -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return new InetAddress[] {addr};
                },
                Duration.ofSeconds(2),
                Duration.ofMinutes(1),
                Clock.systemUTC(),
                1,
                8)) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                java.util.concurrent.Future<InetAddress[]> first =
                        pool.submit(() -> lookup.resolve("coalesce.example"));
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                java.util.concurrent.Future<InetAddress[]> second =
                        pool.submit(() -> lookup.resolve("coalesce.example"));
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
                while (lookup.coalescedCount() < 1L && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertEquals(1L, lookup.coalescedCount(), "second resolve must join in-flight lookup");
                release.countDown();
                assertArrayEquals(new InetAddress[] {addr}, first.get(3, java.util.concurrent.TimeUnit.SECONDS));
                assertArrayEquals(new InetAddress[] {addr}, second.get(3, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(1, calls.get());
            } finally {
                release.countDown();
                pool.shutdownNow();
            }
        }
    }

    @Test
    void rejectsWhenQueueIsFull() throws Exception {
        java.util.concurrent.CountDownLatch blockers = new java.util.concurrent.CountDownLatch(1);
        try (BoundedForwardDnsLookup lookup = BoundedForwardDnsLookup.forTests(
                hostname -> {
                    blockers.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return new InetAddress[] {InetAddress.getByName("1.1.1.1")};
                },
                Duration.ofSeconds(2),
                Duration.ofMinutes(1),
                Clock.systemUTC(),
                1,
                1)) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                pool.submit(() -> lookup.resolve("hold-1.example"));
                pool.submit(() -> lookup.resolve("hold-2.example"));
                // Give workers time to fill the single worker + single queue slot.
                Thread.sleep(100);
                assertThrows(
                        java.util.concurrent.RejectedExecutionException.class,
                        () -> lookup.resolve("overflow.example"));
                assertTrue(lookup.rejectedCount() >= 1);
                assertEquals(lookup.rejectedCount(), lookup.droppedCount());
                DnsOpsStats stats = lookup.opsStats();
                assertTrue(stats.hasPressure());
                assertEquals(1, stats.queueCapacity());
            } finally {
                blockers.countDown();
                pool.shutdownNow();
            }
        }
    }
}
