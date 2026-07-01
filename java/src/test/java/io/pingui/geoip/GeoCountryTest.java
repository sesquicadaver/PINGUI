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

    @Test
    void loopbackAndLinkLocalReturnLan() {
        assertEquals("LAN", GeoCountry.lookup("127.0.0.1"));
        assertEquals("LAN", GeoCountry.lookup("169.254.10.1"));
    }

    @Test
    void lookupNullWhenDisabledOrNullIp(@TempDir Path tempDir) {
        GeoCountry.configure(false, tempDir.resolve("unused.yaml"));
        assertNull(GeoCountry.lookup("8.8.8.8"));
        assertNull(GeoCountry.lookup(null));
    }

    @Test
    void longestPrefixMatchWins(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("hints.yaml");
        java.nio.file.Files.writeString(
                hints,
                """
                prefixes:
                  203.0.113.0/24: AA
                  203.0.113.0/28: BB
                """,
                java.nio.charset.StandardCharsets.UTF_8);
        GeoCountry.configure(true, hints);
        assertEquals("BB", GeoCountry.lookup("203.0.113.10"));
        assertEquals("AA", GeoCountry.lookup("203.0.113.200"));
    }

    @Test
    void invalidHintsFileRejected(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("bad.yaml");
        java.nio.file.Files.writeString(hints, "prefixes:\n  not-cidr: US\n", java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> GeoCountry.configure(true, hints));
    }

    @Test
    void ipv6AddressReturnsNull() {
        assertNull(GeoCountry.lookup("2001:db8::1"));
    }

    @Test
    void yamlWithoutPrefixesUsesBundledHints(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("empty.yaml");
        java.nio.file.Files.writeString(hints, "{}\n", java.nio.charset.StandardCharsets.UTF_8);
        GeoCountry.configure(true, hints);
        assertEquals("US", GeoCountry.lookup("8.8.8.8"));
    }

    @Test
    void defaultRouteMatchesAnyPublicIp(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("catchall.yaml");
        java.nio.file.Files.writeString(
                hints,
                """
                prefixes:
                  0.0.0.0/0: ZZ
                """,
                java.nio.charset.StandardCharsets.UTF_8);
        GeoCountry.configure(true, hints);
        assertEquals("ZZ", GeoCountry.lookup("41.41.41.41"));
    }

    @Test
    void invalidYamlRootRejected(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("scalar.yaml");
        java.nio.file.Files.writeString(hints, "not-a-map\n", java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> GeoCountry.configure(true, hints));
    }

    @Test
    void prefixesMustBeMapping(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("list.yaml");
        java.nio.file.Files.writeString(hints, "prefixes: []\n", java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> GeoCountry.configure(true, hints));
    }

    @Test
    void invalidCountryCodeRejected(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("code.yaml");
        java.nio.file.Files.writeString(
                hints, "prefixes:\n  203.0.113.0/24: USA\n", java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> GeoCountry.configure(true, hints));
    }

    @Test
    void invalidPrefixLengthRejected(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("bits.yaml");
        java.nio.file.Files.writeString(
                hints, "prefixes:\n  203.0.113.0/33: AA\n", java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> GeoCountry.configure(true, hints));
    }

    @Test
    void cidrWithoutSlashRejected(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("noslash.yaml");
        java.nio.file.Files.writeString(
                hints, "prefixes:\n  203.0.113.0: AA\n", java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> GeoCountry.configure(true, hints));
    }

    @Test
    void invalidNetworkInCidrRejected(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("badnet.yaml");
        java.nio.file.Files.writeString(
                hints, "prefixes:\n  999.999.999.999/24: AA\n", java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> GeoCountry.configure(true, hints));
    }
}
