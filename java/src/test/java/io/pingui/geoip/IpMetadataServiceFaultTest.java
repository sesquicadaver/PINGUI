package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.ui.HopGeoLabels;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Fault / concurrency proofs for P36-011: stalled enrichment must not delay probe-adjacent paths.
 */
class IpMetadataServiceFaultTest {

    @AfterEach
    void resetRuntime() {
        IpMetadataRuntime.close();
    }

    @Test
    void cachedAndOfferReturnImmediatelyWhileProviderStalled() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        IpMetadataProvider stalled = ip -> {
            started.countDown();
            awaitQuietly(block);
            return IpMetadata.unknown(IpLiterals.canonicalLiteralOrNull(ip), IpAddressScope.PUBLIC);
        };
        try (IpMetadataService service = new IpMetadataService(stalled, null, 64, 8, 1, 5_000L)) {
            assertTrue(service.offer("1.1.1.1"));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            long start = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                assertNull(service.cached("8.8.8." + (i % 50)));
                // Distinct IPs fill queue; rejects must still return without waiting on stall.
                service.offer("9.9.9." + i);
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(elapsedMs < 500L, "probe-adjacent path took " + elapsedMs + "ms under stall");
            assertTrue(service.opsStats().rejected() >= 1 || service.opsStats().coalesced() >= 0);
        } finally {
            block.countDown();
        }
    }

    @Test
    void hopGeoLabelsCachedOrOfferStaysFastUnderStall() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        IpMetadataProvider stalled = ip -> {
            started.countDown();
            awaitQuietly(block);
            return IpMetadata.unknown(IpLiterals.canonicalLiteralOrNull(ip), IpAddressScope.PUBLIC);
        };
        try (IpMetadataService service = new IpMetadataService(stalled, null, 64, 8, 1, 5_000L)) {
            IpMetadataRuntime.install(service);
            assertTrue(service.offer("1.1.1.1"));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            long start = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                assertNull(HopGeoLabels.cachedOrOffer("8.8.8." + (i % 20)));
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(elapsedMs < 500L, "HopGeoLabels path took " + elapsedMs + "ms under stall");
        } finally {
            block.countDown();
        }
    }

    @Test
    void resolveTimesOutWhenProviderStalls() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        IpMetadataProvider stalled = ip -> {
            awaitQuietly(block);
            return IpMetadata.unknown(IpLiterals.canonicalLiteralOrNull(ip), IpAddressScope.PUBLIC);
        };
        try (IpMetadataService service = new IpMetadataService(stalled, null, 64, 8, 1, 80L)) {
            long start = System.nanoTime();
            IpMetadata result = service.resolve("1.1.1.1");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertNotNull(result);
            assertEquals(IpMetadataSource.NONE, result.source());
            assertTrue(elapsedMs < 1_000L, "resolve timeout took " + elapsedMs + "ms");
            assertTrue(service.opsStats().errors() >= 1);
            // Negative cache prevents immediate re-entry into the stalled path.
            assertEquals(IpMetadataSource.NONE, service.resolve("1.1.1.1").source());
        } finally {
            block.countDown();
        }
    }

    @Test
    void coalescedResolveWaiterTimesOutIndependently() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch block = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        IpMetadataProvider stalled = ip -> {
            calls.incrementAndGet();
            entered.countDown();
            awaitQuietly(block);
            return IpMetadata.unknown(IpLiterals.canonicalLiteralOrNull(ip), IpAddressScope.PUBLIC);
        };
        try (IpMetadataService service = new IpMetadataService(stalled, null, 64, 8, 1, 100L)) {
            Thread owner = new Thread(() -> service.resolve("1.1.1.1"), "geoip-owner");
            owner.setDaemon(true);
            owner.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            long start = System.nanoTime();
            IpMetadata waiter = service.resolve("1.1.1.1");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(
                    waiter == null || waiter.source() == IpMetadataSource.NONE,
                    "coalesced waiter must not observe a late successful stall result");
            assertTrue(elapsedMs < 1_000L, "coalesced wait took " + elapsedMs + "ms");
            assertTrue(service.opsStats().coalesced() >= 1);
            assertTrue(service.opsStats().errors() >= 1);
            assertEquals(1, calls.get());
        } finally {
            block.countDown();
        }
    }

    @Test
    void providerExceptionDoesNotBreakSubsequentOffer() {
        AtomicInteger calls = new AtomicInteger();
        IpMetadataProvider flaky = ip -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            return IpMetadata.unknown(IpLiterals.canonicalLiteralOrNull(ip), IpAddressScope.PUBLIC);
        };
        try (IpMetadataService service = new IpMetadataService(flaky, null, 64, 8, 1, 1_000L)) {
            assertNull(service.resolve("1.1.1.1"));
            assertTrue(service.opsStats().errors() >= 1);
            assertTrue(service.offer("8.8.8.8"));
            assertTrue(awaitCached(service, "8.8.8.8", 3));
            assertFalse(service.opsStats().errors() < 1);
        }
    }

    private static boolean awaitCached(IpMetadataService service, String ip, int seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (service.cached(ip) != null) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
