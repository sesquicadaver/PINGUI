package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.geoip.IpMetadata;
import io.pingui.geoip.IpMetadataRuntime;
import io.pingui.geoip.IpMetadataService;
import io.pingui.geoip.IpMetadataSource;
import io.pingui.geoip.MergingIpMetadataProvider;
import io.pingui.geoip.YamlIpMetadataOverrides;
import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HopGeoLabelsTest {
    @BeforeEach
    void setUp() {
        IpMetadataRuntime.install(IpMetadataService.overridesOnly(MergingIpMetadataProvider.of(
                YamlIpMetadataOverrides.fromResource("geoip_hints.yaml"),
                YamlIpMetadataOverrides.fromResource("asn_hints.yaml"))));
    }

    @AfterEach
    void tearDown() {
        IpMetadataRuntime.close();
    }

    @Test
    void formatStripJoinsLanAndPublicTokens() {
        IpMetadataRuntime.get().resolve("192.168.0.1");
        IpMetadataRuntime.get().resolve("8.8.8.8");
        String strip = HopGeoLabels.formatStrip(
                List.of(new HopNode(1, "192.168.0.1", 1.0, false), new HopNode(2, "8.8.8.8", 5.0, false)));
        assertEquals("LAN → US/AS15169", strip);
    }

    @Test
    void detailsIncludesSourceAndApproximate() {
        YamlIpMetadataOverrides overrides = YamlIpMetadataOverrides.fromYaml(
                """
                prefixes:
                  8.8.8.8/32:
                    country: US
                    city: Mountain View
                    latitude: 37.4
                    longitude: -122.1
                    accuracy_radius_km: 100
                    asn: 15169
                    organization: Google
                """);
        IpMetadataRuntime.install(IpMetadataService.overridesOnly(overrides));
        IpMetadata meta = IpMetadataRuntime.get().resolve("8.8.8.8");
        String details = HopGeoLabels.details(meta);
        assertTrue(details.contains("country: US"));
        assertTrue(details.contains("city: Mountain View"));
        assertTrue(details.contains("ASN: AS15169 Google"));
        assertTrue(details.contains("source: " + IpMetadataSource.OVERRIDE));
        assertTrue(details.contains("accuracy: ~100 km"));
        assertTrue(details.contains("approximate"));
    }

    @Test
    void formatStripSkipsTimeoutsAndEmpty() {
        assertEquals("", HopGeoLabels.formatStrip(List.of()));
        assertEquals("", HopGeoLabels.formatStrip(List.of(new HopNode(1, "*", null, true))));
    }
}
