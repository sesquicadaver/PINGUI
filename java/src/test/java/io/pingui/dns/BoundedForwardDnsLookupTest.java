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
    void cachesTimeoutFailureBriefly() {
        AtomicInteger calls = new AtomicInteger();
        try (BoundedForwardDnsLookup lookup = BoundedForwardDnsLookup.forTests(
                hostname -> {
                    calls.incrementAndGet();
                    Thread.sleep(200);
                    return new InetAddress[] {InetAddress.getByName("8.8.8.8")};
                },
                Duration.ofMillis(30),
                Duration.ofMinutes(1),
                Clock.systemUTC())) {
            assertThrows(SocketTimeoutException.class, () -> lookup.resolve("fail.example"));
            assertThrows(SocketTimeoutException.class, () -> lookup.resolve("fail.example"));
            assertTrue(calls.get() >= 1);
            assertEquals(1, calls.get());
        }
    }
}
