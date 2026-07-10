package io.pingui.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DnsResolverTest {
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-07-10T12:00:00Z"));
        DnsResolver.configureForTests(true, Duration.ofMinutes(5), clock, addr -> null);
    }

    @AfterEach
    void tearDown() {
        DnsResolver.resetForTests();
    }

    @Test
    void hostnameInputDoesNotScheduleLookup() {
        AtomicInteger calls = new AtomicInteger();
        DnsResolver.configureForTests(true, Duration.ofMinutes(5), clock, addr -> {
            calls.incrementAndGet();
            return "should-not-run";
        });
        assertNull(DnsResolver.cachedHostname("router.example"));
        assertNull(DnsResolver.cachedHostname("*"));
        assertEquals("", DnsResolver.labelLine("router.example"));
        assertEquals(0, calls.get());
        assertEquals(0, DnsResolver.cacheSizeForTests());
    }

    @Test
    void positiveCacheReturnsLabelLine() {
        DnsResolver.putCache("8.8.8.8", "dns.google");
        assertEquals("dns.google", DnsResolver.cachedHostname("8.8.8.8"));
        assertEquals("\ndns.google", DnsResolver.labelLine("8.8.8.8"));
    }

    @Test
    void negativeCacheDoesNotReturnHostname() {
        DnsResolver.putCache("10.0.0.1", null);
        assertNull(DnsResolver.cachedHostname("10.0.0.1"));
        assertEquals("", DnsResolver.labelLine("10.0.0.1"));
    }

    @Test
    void cacheExpiresAfterTtl() {
        DnsResolver.putCache("1.1.1.1", "one.one.one.one");
        assertEquals("one.one.one.one", DnsResolver.cachedHostname("1.1.1.1"));
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertNull(DnsResolver.cachedHostname("1.1.1.1"));
    }

    @Test
    void asyncLookupNotifiesListenerOnPositiveResult() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger();
        DnsResolver.configureForTests(true, Duration.ofMinutes(5), clock, addr -> {
            lookups.incrementAndGet();
            return "ptr.example";
        });
        Runnable listener = done::countDown;
        DnsResolver.addListener(listener);
        try {
            assertNull(DnsResolver.cachedHostname("203.0.113.10"));
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals(1, lookups.get());
            assertEquals("ptr.example", DnsResolver.cachedHostname("203.0.113.10"));
            assertEquals("\nptr.example", DnsResolver.labelLine("203.0.113.10"));
        } finally {
            DnsResolver.removeListener(listener);
        }
    }

    @Test
    void disabledSkipsLookup() {
        AtomicInteger calls = new AtomicInteger();
        DnsResolver.configureForTests(false, Duration.ofMinutes(5), clock, addr -> {
            calls.incrementAndGet();
            return "x";
        });
        assertNull(DnsResolver.cachedHostname("8.8.4.4"));
        assertEquals(0, calls.get());
    }

    @Test
    void reverseReturningAddressIsTreatedAsNegativeCache() throws Exception {
        CountDownLatch reverseDone = new CountDownLatch(1);
        DnsResolver.configureForTests(true, Duration.ofMinutes(5), clock, addr -> {
            reverseDone.countDown();
            return addr.getHostAddress();
        });
        assertNull(DnsResolver.cachedHostname("198.51.100.20"));
        assertTrue(reverseDone.await(3, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (DnsResolver.cacheSizeForTests() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, DnsResolver.cacheSizeForTests());
        assertNull(DnsResolver.cachedHostname("198.51.100.20"));
        assertEquals("", DnsResolver.labelLine("198.51.100.20"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
