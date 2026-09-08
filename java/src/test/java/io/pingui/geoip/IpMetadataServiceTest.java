package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IpMetadataServiceTest {
    private static final Path CITY = copyResource("mmdb/GeoLite2-City-Test.mmdb");
    private static final Path COUNTRY = copyResource("mmdb/GeoLite2-Country-Test.mmdb");
    private static final Path ASN = copyResource("mmdb/GeoLite2-ASN-Test.mmdb");

    @Test
    void precedenceYamlOverMmdbAndNegativeCache() throws Exception {
        YamlIpMetadataOverrides overrides = YamlIpMetadataOverrides.fromYaml(
                """
                prefixes:
                  81.2.69.160/32: UA
                """);
        try (IpMetadataService service = new IpMetadataService(overrides, MmdbIpMetadataProvider.open(CITY, ASN))) {
            IpMetadata override = service.resolve("81.2.69.160");
            assertEquals(IpMetadataSource.OVERRIDE, override.source());
            assertEquals("UA", override.countryIso());
            assertEquals(override, service.cached("81.2.69.160"));

            IpMetadata miss = service.resolve("8.8.8.8");
            assertEquals(IpMetadataSource.NONE, miss.source());
            assertEquals(miss, service.cached("8.8.8.8"));
            assertTrue(service.opsStats().unknowns() >= 1);

            long missesBefore = service.opsStats().misses();
            assertEquals(IpMetadataSource.NONE, service.resolve("8.8.8.8").source());
            assertEquals(missesBefore, service.opsStats().misses(), "negative cache must skip provider");
        }
    }

    @Test
    void mmdbHitWhenNoOverride() throws Exception {
        try (IpMetadataService service = new IpMetadataService(null, MmdbIpMetadataProvider.open(CITY))) {
            IpMetadata meta = service.resolve("81.2.69.160");
            assertEquals(IpMetadataSource.MMDB, meta.source());
            assertEquals("GB", meta.countryIso());
            assertEquals("London", meta.city());
        }
    }

    @Test
    void specialWithoutOverrideIsUnknownNotMmdb() throws Exception {
        try (IpMetadataService service = new IpMetadataService(null, MmdbIpMetadataProvider.open(CITY))) {
            IpMetadata doc = service.resolve("203.0.113.10");
            assertEquals(IpAddressScope.SPECIAL, doc.scope());
            assertEquals(IpMetadataSource.NONE, doc.source());
            assertNull(doc.countryIso());
        }
    }

    @Test
    void lruEvictsOldestEntries() {
        CountingProvider provider = new CountingProvider();
        try (IpMetadataService service = new IpMetadataService(provider, null, 2, 8, 1)) {
            service.resolve("1.1.1.1");
            service.resolve("8.8.8.8");
            assertEquals(2, service.cacheSizeForTests());
            service.resolve("9.9.9.9");
            assertEquals(2, service.cacheSizeForTests());
            assertNull(service.cached("1.1.1.1"));
            assertNotNull(service.cached("8.8.8.8"));
            assertNotNull(service.cached("9.9.9.9"));
            assertTrue(provider.calls.get() >= 3);
        }
    }

    @Test
    void offerCoalescesAndRejectsWhenQueueFull() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        IpMetadataProvider blocking = ip -> {
            started.countDown();
            try {
                assertTrue(block.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return IpMetadata.unknown(IpLiterals.canonicalLiteralOrNull(ip), IpAddressScope.PUBLIC);
        };
        try (IpMetadataService service = new IpMetadataService(blocking, null, 64, 1, 1)) {
            assertTrue(service.offer("1.1.1.1"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            // One more fills the single-slot queue; further offers reject.
            assertTrue(service.offer("8.8.8.8"));
            boolean rejectedSeen = false;
            for (int i = 0; i < 8; i++) {
                if (!service.offer("9.9.9." + i)) {
                    rejectedSeen = true;
                    break;
                }
            }
            assertTrue(rejectedSeen);
            assertTrue(service.opsStats().rejected() >= 1);
            block.countDown();
            assertTrue(awaitCached(service, "1.1.1.1", 5));
        }
    }

    @Test
    void concurrentResolveDedupesInFlight() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        IpMetadataProvider slow = ip -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return IpMetadata.unknown(IpLiterals.canonicalLiteralOrNull(ip), IpAddressScope.PUBLIC);
        };
        try (IpMetadataService service = new IpMetadataService(slow, null, 64, 8, 1);
                ExecutorService pool = Executors.newFixedThreadPool(4)) {
            java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(4);
            List<Future<IpMetadata>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return service.resolve("1.1.1.1");
                }));
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(1, service.inFlightSizeForTests());
            release.countDown();
            for (Future<IpMetadata> future : futures) {
                assertEquals(
                        IpMetadataSource.NONE, future.get(5, TimeUnit.SECONDS).source());
            }
            assertEquals(1, calls.get());
            assertTrue(service.opsStats().coalesced() >= 1);
        }
    }

    @Test
    void atomicReloadKeepsOldOnFailureAndSwapsOnSuccess() throws Exception {
        try (IpMetadataService service = new IpMetadataService(null, MmdbIpMetadataProvider.open(CITY))) {
            assertEquals("GB", service.resolve("81.2.69.160").countryIso());
            assertTrue(service.cached("81.2.69.160") != null);

            Path corrupt = Files.createTempFile("pingui-reload-bad-", ".mmdb");
            try {
                Files.writeString(corrupt, "not-mmdb", StandardCharsets.UTF_8);
                assertFalse(service.reloadMmdb(corrupt, null));
                assertEquals(1, service.opsStats().reloadFailures());
                assertEquals("GB", service.resolve("81.2.69.160").countryIso());
            } finally {
                try {
                    Files.deleteIfExists(corrupt);
                } catch (IOException ex) {
                    corrupt.toFile().deleteOnExit();
                }
            }

            assertTrue(service.reloadMmdb(COUNTRY, ASN));
            assertNull(service.cached("81.2.69.160"), "reload must clear cache");
            IpMetadata after = service.resolve("81.2.69.160");
            assertEquals(IpMetadataSource.MMDB, after.source());
            assertEquals("GB", after.countryIso());
            assertNull(after.city());
            assertNotNull(service.mmdb());
            assertTrue(service.mmdb().hasAsnDatabase());
            assertEquals(1, service.opsStats().reloadFailures());
        }
    }

    @Test
    void reloadUnderParallelLookups() throws Exception {
        try (IpMetadataService service = new IpMetadataService(null, MmdbIpMetadataProvider.open(CITY));
                ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                futures.add(pool.submit(() -> {
                    for (int n = 0; n < 20; n++) {
                        service.resolve("81.2.69.160");
                        service.resolve("8.8.8.8");
                    }
                }));
            }
            assertTrue(service.reloadMmdb(COUNTRY, null));
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            IpMetadata meta = service.resolve("81.2.69.160");
            assertEquals("GB", meta.countryIso());
            assertNull(meta.city());
        }
    }

    @Test
    void nonLiteralsAreIgnored() {
        try (IpMetadataService service = IpMetadataService.disabled()) {
            assertNull(service.resolve("dns.google"));
            assertFalse(service.offer("dns.google"));
            assertNull(service.cached("dns.google"));
        }
    }

    private static boolean awaitCached(IpMetadataService service, String ip, int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (service.cached(ip) != null) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static final class CountingProvider implements IpMetadataProvider {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public IpMetadata lookup(String ip) {
            calls.incrementAndGet();
            String canonical = IpLiterals.canonicalLiteralOrNull(ip);
            return IpMetadata.unknown(canonical, IpAddressScope.PUBLIC);
        }
    }

    private static Path copyResource(String resource) {
        try {
            Path target =
                    Files.createTempFile("pingui-", "-" + Path.of(resource).getFileName());
            target.toFile().deleteOnExit();
            try (InputStream in = IpMetadataServiceTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Missing test resource: " + resource);
                }
                Files.write(target, in.readAllBytes());
            }
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
