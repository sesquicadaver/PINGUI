package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MergingIpMetadataProviderTest {
    @Test
    void ofReturnsSingleWhenOtherNull() {
        YamlIpMetadataOverrides geo = YamlIpMetadataOverrides.fromResource("geoip_hints.yaml");
        assertSame(geo, MergingIpMetadataProvider.of(geo, null));
        assertSame(geo, MergingIpMetadataProvider.of(null, geo));
    }

    @Test
    void primaryCountryWinsSecondaryFillsAsn() {
        IpMetadataProvider merged = MergingIpMetadataProvider.of(
                YamlIpMetadataOverrides.fromResource("geoip_hints.yaml"),
                YamlIpMetadataOverrides.fromResource("asn_hints.yaml"));
        IpMetadata meta = merged.lookup("8.8.8.8");
        assertEquals("US", meta.countryIso());
        assertEquals(15169, meta.asn());
        assertEquals("Google", meta.organization());
        assertEquals(IpMetadataSource.OVERRIDE, meta.source());
    }

    @Test
    void secondaryOnlyWhenPrimaryMisses() {
        IpMetadataProvider geoOnly = YamlIpMetadataOverrides.fromYaml("prefixes: {}\n");
        IpMetadataProvider asn = YamlIpMetadataOverrides.fromResource("asn_hints.yaml");
        IpMetadataProvider merged = MergingIpMetadataProvider.of(geoOnly, asn);
        IpMetadata meta = merged.lookup("8.8.8.8");
        assertNull(meta.countryIso());
        assertEquals(15169, meta.asn());
        assertEquals("Google", meta.organization());
    }

    @Test
    void hostnameReturnsNull() {
        IpMetadataProvider merged = MergingIpMetadataProvider.of(
                YamlIpMetadataOverrides.fromResource("geoip_hints.yaml"),
                YamlIpMetadataOverrides.fromResource("asn_hints.yaml"));
        assertNull(merged.lookup("dns.google"));
    }

    @Test
    void ipv6AsnHintsMerge() {
        IpMetadataProvider merged = MergingIpMetadataProvider.of(
                YamlIpMetadataOverrides.fromResource("geoip_hints.yaml"),
                YamlIpMetadataOverrides.fromResource("asn_hints.yaml"));
        IpMetadata meta = merged.lookup("2001:4860:4860::8888");
        assertEquals("US", meta.countryIso());
        assertEquals(15169, meta.asn());
        assertTrue(meta.organization().contains("Google"));
    }
}
