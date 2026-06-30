package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoCountryTest {
    @AfterEach
    void reset() {
        GeoCountry.configure(true, Path.of("config/geoip_hints.yaml"));
    }

    @Test
    void privateIpReturnsLan() {
        assertEquals("LAN", GeoCountry.lookup("10.0.0.1"));
        assertEquals("LAN", GeoCountry.lookup("192.168.0.1"));
    }

    @Test
    void bundledHintsMatchPublicDns() {
        assertEquals("US", GeoCountry.lookup("8.8.8.8"));
        assertEquals("AU", GeoCountry.lookup("1.1.1.1"));
    }

    @Test
    void unknownPublicIpReturnsNull() {
        assertNull(GeoCountry.lookup("41.41.41.41"));
    }

    @Test
    void disableGeoip(@TempDir Path tempDir) throws Exception {
        GeoCountry.configure(false, tempDir.resolve("unused.yaml"));
        assertNull(GeoCountry.lookup("8.8.8.8"));
    }

    @Test
    void customHintsFile(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("hints.yaml");
        java.nio.file.Files.writeString(
                hints, "prefixes:\n  41.41.41.0/24: XX\n", java.nio.charset.StandardCharsets.UTF_8);
        GeoCountry.configure(true, hints);
        assertEquals("XX", GeoCountry.lookup("41.41.41.5"));
    }

    @Test
    void lookupReturnsNullForBlankOrInvalid() {
        assertNull(GeoCountry.lookup(" "));
        assertNull(GeoCountry.lookup("not-an-ip"));
    }

    @Test
    void lookupReturnsNullForMulticast() {
        assertNull(GeoCountry.lookup("239.255.255.255"));
    }

    @Test
    void configureMissingFileUsesBundledHints(@TempDir Path tempDir) {
        GeoCountry.configure(true, tempDir.resolve("missing.yaml"));
        assertEquals("US", GeoCountry.lookup("8.8.8.8"));
    }
}
