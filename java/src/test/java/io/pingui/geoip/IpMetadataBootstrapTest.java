package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.AppOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IpMetadataBootstrapTest {
    private static final Path CITY = copyResource("mmdb/GeoLite2-City-Test.mmdb");
    private static final Path ASN = copyResource("mmdb/GeoLite2-ASN-Test.mmdb");

    @Test
    void disabledWhenNoGeoip() {
        AppOptions options = withGeoip(false, null, null, null, true);
        try (IpMetadataService service = IpMetadataBootstrap.open(options)) {
            assertNull(service.mmdb());
            IpMetadata meta = service.resolve("8.8.8.8");
            assertEquals(IpMetadataSource.NONE, meta.source());
        }
    }

    @Test
    void yamlOnlyWhenMmdbUnsetUsesBundledHints() {
        AppOptions options = withGeoip(true, Path.of("config/geoip_hints.yaml"), null, null, true);
        try (IpMetadataService service = IpMetadataBootstrap.open(options)) {
            assertNull(service.mmdb());
            IpMetadata meta = service.resolve("8.8.8.8");
            assertEquals("US", meta.countryIso());
            assertEquals(15169, meta.asn());
            assertEquals("Google", meta.organization());
            assertEquals(IpMetadataSource.OVERRIDE, meta.source());
        }
    }

    @Test
    void noAsnSkipsAsnHintsMerge() {
        AppOptions options = withGeoip(true, Path.of("config/geoip_hints.yaml"), null, null, false);
        try (IpMetadataService service = IpMetadataBootstrap.open(options)) {
            IpMetadata meta = service.resolve("8.8.8.8");
            assertEquals("US", meta.countryIso());
            assertNull(meta.asn());
        }
    }

    @Test
    void opensMmdbAndOptionalAsn(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("empty-hints.yaml");
        Files.writeString(hints, "prefixes: {}\n", StandardCharsets.UTF_8);
        AppOptions options = withGeoip(true, hints, CITY, ASN, true);
        try (IpMetadataService service = IpMetadataBootstrap.open(options)) {
            assertNotNull(service.mmdb());
            assertTrue(service.mmdb().hasAsnDatabase());
            IpMetadata meta = service.resolve("81.2.69.160");
            assertEquals(IpMetadataSource.MMDB, meta.source());
            assertEquals("GB", meta.countryIso());
        }
    }

    @Test
    void noAsnSkipsAsnMmdb(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("empty-hints.yaml");
        Files.writeString(hints, "prefixes: {}\n", StandardCharsets.UTF_8);
        AppOptions options = withGeoip(true, hints, CITY, ASN, false);
        try (IpMetadataService service = IpMetadataBootstrap.open(options)) {
            assertNotNull(service.mmdb());
            assertFalse(service.mmdb().hasAsnDatabase());
        }
    }

    @Test
    void explicitMissingOrCorruptMmdbIsConfigError() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> IpMetadataBootstrap.open(withGeoip(
                        true, Path.of("config/geoip_hints.yaml"), Path.of("missing-geoip.mmdb"), null, true)));

        Path corrupt = Files.createTempFile("pingui-boot-bad-", ".mmdb");
        try {
            Files.writeString(corrupt, "not-mmdb", StandardCharsets.UTF_8);
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> IpMetadataBootstrap.open(
                            withGeoip(true, Path.of("config/geoip_hints.yaml"), corrupt, null, true)));
            assertTrue(ex.getMessage().contains("Failed to open GeoIP MMDB"));
        } finally {
            try {
                Files.deleteIfExists(corrupt);
            } catch (IOException ex) {
                corrupt.toFile().deleteOnExit();
            }
        }
    }

    @Test
    void rejectsAsnDbWithoutGeoDbAndNoGeoipConflict() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IpMetadataBootstrap.open(
                        withGeoip(true, Path.of("config/geoip_hints.yaml"), null, Path.of("asn.mmdb"), true)));
        assertThrows(
                IllegalArgumentException.class,
                () -> IpMetadataBootstrap.open(
                        withGeoip(false, Path.of("config/geoip_hints.yaml"), Path.of("city.mmdb"), null, true)));
    }

    private static AppOptions withGeoip(boolean geoipEnabled, Path hints, Path geoDb, Path asnDb, boolean asnEnabled) {
        AppOptions d = AppOptions.defaults();
        return new AppOptions(
                d.configPath(),
                d.profileOverrides(),
                d.alertOverrides(),
                d.persistenceOverrides(),
                d.telemetryOverrides(),
                d.timeSeriesOverrides(),
                false,
                geoipEnabled,
                hints != null ? hints : d.geoipHintsPath(),
                Optional.ofNullable(geoDb),
                Optional.ofNullable(asnDb),
                asnEnabled,
                d.asnHintsPath(),
                d.asnTimeoutMs(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                d.runMode(),
                d.pidFilePath(),
                Optional.empty(),
                Optional.empty(),
                d.telemetryRetentionDays(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static Path copyResource(String resource) {
        try {
            Path target =
                    Files.createTempFile("pingui-", "-" + Path.of(resource).getFileName());
            target.toFile().deleteOnExit();
            try (InputStream in = IpMetadataBootstrapTest.class.getClassLoader().getResourceAsStream(resource)) {
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
