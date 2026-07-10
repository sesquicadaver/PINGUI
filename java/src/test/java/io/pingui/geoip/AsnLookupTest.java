package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsnLookupTest {
    @AfterEach
    void restoreDefaults() {
        AsnLookup.configure(true, Path.of("config/asn_hints.yaml"), AsnLookup.DEFAULT_TIMEOUT_MS);
    }

    @Test
    void hostnameDoesNotTriggerDnsLookup() {
        AsnLookup.configure(true, Path.of("config/asn_hints.yaml"));
        assertNull(AsnLookup.lookup("dns.google"));
        assertNull(AsnLookup.lookup("router.example.com"));
        assertEquals("", AsnLookup.labelLine("dns.google"));
    }

    @Test
    void lookupPublicDnsFromDefaultHints() {
        AsnLookup.configure(true, Path.of("config/asn_hints.yaml"));
        AsnInfo google = AsnLookup.lookup("8.8.8.8");
        assertEquals(15169, google.asn());
        assertEquals("Google", google.org());
        assertEquals("AS15169 Google", google.label());
        assertEquals("\nAS15169 Google", AsnLookup.labelLine("8.8.8.8"));
    }

    @Test
    void longestPrefixWins(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("asn.yaml");
        Files.writeString(
                hints,
                """
                prefixes:
                  8.8.0.0/16: { asn: 1, org: Broad }
                  8.8.8.0/24: { asn: 15169, org: Google }
                """);
        AsnLookup.configure(true, hints);
        assertEquals(15169, AsnLookup.lookup("8.8.8.8").asn());
        assertEquals(1, AsnLookup.lookup("8.8.1.1").asn());
    }

    @Test
    void privateAndDisabledReturnNull() {
        AsnLookup.configure(true, Path.of("config/asn_hints.yaml"));
        assertNull(AsnLookup.lookup("192.168.0.1"));
        assertEquals("", AsnLookup.labelLine("192.168.0.1"));
        AsnLookup.configure(false, Path.of("config/asn_hints.yaml"));
        assertNull(AsnLookup.lookup("8.8.8.8"));
    }

    @Test
    void timeoutMsStoredForFutureWhois() {
        AsnLookup.configure(true, Path.of("config/asn_hints.yaml"), 1500);
        assertEquals(1500, AsnLookup.timeoutMs());
    }

    @Test
    void ipv6Lookup(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("asn6.yaml");
        Files.writeString(
                hints,
                """
                prefixes_v6:
                  2001:4860:4860::/48: { asn: 15169, org: Google }
                """);
        AsnLookup.configure(true, hints);
        assertEquals(15169, AsnLookup.lookup("2001:4860:4860::8888").asn());
    }

    @Test
    void invalidYamlRejected(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("bad.yaml");
        Files.writeString(hints, "prefixes:\n  8.8.8.0/24: US\n");
        assertThrows(IllegalArgumentException.class, () -> AsnLookup.configure(true, hints));
    }

    @Test
    void asnInfoRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new AsnInfo(-1, "x"));
        assertTrue(new AsnInfo(0, "  ").label().equals("AS0"));
    }
}
