package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlIpMetadataOverridesTest {
    @Test
    void legacyCountryHintsLongestPrefixAndBundledFile() {
        YamlIpMetadataOverrides provider = YamlIpMetadataOverrides.fromResource("geoip_hints.yaml");
        IpMetadata google = provider.lookup("8.8.8.8");
        assertEquals("US", google.countryIso());
        assertEquals(IpMetadataSource.OVERRIDE, google.source());
        assertEquals(IpAddressScope.PUBLIC, google.scope());
        assertEquals("AU", provider.lookup("1.1.1.1").countryIso());
        assertNull(provider.lookup("41.41.41.41"));
        assertNull(provider.lookup("dns.google"));
    }

    @Test
    void legacyLongestPrefixWins(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("hints.yaml");
        Files.writeString(
                hints,
                """
                prefixes:
                  41.41.41.0/24: AA
                  41.41.41.0/28: BB
                """,
                StandardCharsets.UTF_8);
        YamlIpMetadataOverrides provider = YamlIpMetadataOverrides.fromFile(hints);
        assertEquals("BB", provider.lookup("41.41.41.10").countryIso());
        assertEquals("AA", provider.lookup("41.41.41.200").countryIso());
    }

    @Test
    void extendedMappingSupportsPrivateOverrideWithCoordinates(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("hints.yaml");
        Files.writeString(
                hints,
                """
                prefixes:
                  10.20.0.0/16:
                    label: DC-KYIV
                    country: UA
                    city: Kyiv
                    latitude: 50.45
                    longitude: 30.52
                    asn: 64512
                    organization: Internal Backbone
                prefixes_v6:
                  fd12:3456::/32:
                    country: ua
                    subdivision: Kyiv
                    city: DC
                    asn: 64512
                """,
                StandardCharsets.UTF_8);
        YamlIpMetadataOverrides provider = YamlIpMetadataOverrides.fromFile(hints);
        IpMetadata v4 = provider.lookup("10.20.1.5");
        assertEquals(IpAddressScope.PRIVATE, v4.scope());
        assertEquals(IpMetadataSource.OVERRIDE, v4.source());
        assertEquals("UA", v4.countryIso());
        assertEquals("Kyiv", v4.city());
        assertEquals(50.45, v4.latitude());
        assertEquals(30.52, v4.longitude());
        assertEquals(64512, v4.asn());
        assertEquals("Internal Backbone", v4.organization());
        assertTrue(v4.hasCoordinates());

        IpMetadata v6 = provider.lookup("fd12:3456::99");
        assertEquals(IpAddressScope.PRIVATE, v6.scope());
        assertEquals("UA", v6.countryIso());
        assertEquals("Kyiv", v6.subdivision());
        assertEquals(64512, v6.asn());
    }

    @Test
    void labelFillsOrganizationWhenOrgMissing(@TempDir Path tempDir) throws Exception {
        Path hints = tempDir.resolve("hints.yaml");
        Files.writeString(
                hints,
                """
                prefixes:
                  198.51.100.0/24:
                    country: US
                    label: LAB-NET
                """,
                StandardCharsets.UTF_8);
        // 198.51.100/24 is documentation SPECIAL — explicit override still applies.
        IpMetadata meta = YamlIpMetadataOverrides.fromFile(hints).lookup("198.51.100.10");
        assertEquals(IpAddressScope.SPECIAL, meta.scope());
        assertEquals("US", meta.countryIso());
        assertEquals("LAB-NET", meta.organization());
    }

    @Test
    void fromYamlRejectsInvalidShapes(@TempDir Path tempDir) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> YamlIpMetadataOverrides.fromYaml("[]\n"));
        assertThrows(
                IllegalArgumentException.class,
                () -> YamlIpMetadataOverrides.fromYaml("prefixes:\n  8.8.8.0/24: USA\n"));
        Path bad = tempDir.resolve("bad.yaml");
        Files.writeString(bad, "prefixes:\n  not-cidr: US\n", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> YamlIpMetadataOverrides.fromFile(bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> YamlIpMetadataOverrides.fromYaml("prefixes:\n  8.8.8.0/24: {}\n"));
    }

    @Test
    void emptyRootLoadsZeroRules() {
        YamlIpMetadataOverrides provider = YamlIpMetadataOverrides.fromYaml("{}\n");
        assertEquals(0, provider.ruleCount());
        assertNull(provider.lookup("8.8.8.8"));
    }
}
