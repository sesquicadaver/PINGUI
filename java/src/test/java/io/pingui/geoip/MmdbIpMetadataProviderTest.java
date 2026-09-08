package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MmdbIpMetadataProviderTest {
    private static final Path CITY = copyResource("mmdb/GeoLite2-City-Test.mmdb");
    private static final Path COUNTRY = copyResource("mmdb/GeoLite2-Country-Test.mmdb");
    private static final Path ASN = copyResource("mmdb/GeoLite2-ASN-Test.mmdb");

    @Test
    void opensCityDatabaseAndExposesTypeAndBuildEpoch() throws Exception {
        try (MmdbIpMetadataProvider provider = MmdbIpMetadataProvider.open(CITY)) {
            MmdbDatabaseInfo info = provider.geoDatabaseInfo();
            assertTrue(info.databaseType().toLowerCase().contains("city"));
            assertTrue(info.buildEpochSeconds() > 0);
            assertNull(provider.asnDatabaseInfo());
            assertFalse(provider.hasAsnDatabase());
        }
    }

    @Test
    void cityLookupReturnsMmdbMetadataForKnownTestIp() throws Exception {
        // MaxMind GeoLite2-City-Test fixture — 81.2.69.160 is London, GB.
        try (MmdbIpMetadataProvider provider = MmdbIpMetadataProvider.open(CITY, ASN)) {
            IpMetadata meta = provider.lookup("81.2.69.160");
            assertNotNull(meta);
            assertEquals(IpMetadataSource.MMDB, meta.source());
            assertEquals(IpAddressScope.PUBLIC, meta.scope());
            assertEquals("GB", meta.countryIso());
            assertEquals("London", meta.city());
            assertTrue(meta.hasCoordinates());
            assertEquals(provider.geoDatabaseInfo().buildEpochSeconds(), meta.datasetEpochSeconds());
            assertTrue(provider.hasAsnDatabase());
        }
    }

    @Test
    void countryDatabaseReturnsIsoWithoutCity() throws Exception {
        try (MmdbIpMetadataProvider provider = MmdbIpMetadataProvider.open(COUNTRY)) {
            assertTrue(provider.geoDatabaseInfo().databaseType().toLowerCase().contains("country"));
            IpMetadata meta = provider.lookup("81.2.69.160");
            assertEquals(IpMetadataSource.MMDB, meta.source());
            assertEquals("GB", meta.countryIso());
            assertNull(meta.city());
            assertFalse(meta.hasCoordinates());
        }
    }

    @Test
    void unknownAndSpecialAddresses() throws Exception {
        try (MmdbIpMetadataProvider provider = MmdbIpMetadataProvider.open(CITY)) {
            IpMetadata unknown = provider.lookup("203.0.113.50");
            // 203.0.113/24 is documentation → SPECIAL → skipped
            assertNull(unknown);
            assertNull(provider.lookup("2001:db8::1"));
            assertNull(provider.lookup("dns.google"));
            IpMetadata miss = provider.lookup("8.8.8.8");
            // may be NONE if not in test DB
            assertNotNull(miss);
            assertEquals(IpMetadataSource.NONE, miss.source());
        }
    }

    @Test
    void rejectsWrongEditionAsGeoDb() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> MmdbIpMetadataProvider.open(ASN));
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    @Test
    void rejectsMissingAndCorruptFiles() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> MmdbIpMetadataProvider.open(Path.of("pingui-missing-geoip-db.mmdb")));
        Path corrupt = Files.createTempFile("pingui-corrupt-", ".mmdb");
        try {
            Files.writeString(corrupt, "not-an-mmdb");
            assertThrows(IllegalStateException.class, () -> MmdbIpMetadataProvider.open(corrupt));
        } finally {
            Files.deleteIfExists(corrupt);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> MmdbIpMetadataProvider.open(CITY, Path.of("pingui-missing-asn-db.mmdb")));
    }

    private static Path copyResource(String resource) {
        try {
            Path target =
                    Files.createTempFile("pingui-", "-" + Path.of(resource).getFileName());
            target.toFile().deleteOnExit();
            try (InputStream in =
                    MmdbIpMetadataProviderTest.class.getClassLoader().getResourceAsStream(resource)) {
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
