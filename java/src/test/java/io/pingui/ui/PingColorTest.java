package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.dns.DnsResolver;
import io.pingui.geoip.AsnLookup;
import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PingColorTest {
    @BeforeEach
    void configureGeoipAndAsn() {
        GeoCountry.configure(true, java.nio.file.Path.of("config/geoip_hints.yaml"));
        AsnLookup.configure(true, java.nio.file.Path.of("config/asn_hints.yaml"));
        // Disable live PTR so labels stay deterministic; seed cache where needed.
        DnsResolver.configureForTests(true, Duration.ofMinutes(5), java.time.Clock.systemUTC(), addr -> null);
    }

    @AfterEach
    void resetDns() {
        DnsResolver.resetForTests();
    }

    @Test
    void pingColorThresholds() {
        assertEquals("#98fb98", PingColor.pingColor(10.0, false));
        assertEquals("#ffa500", PingColor.pingColor(100.0, false));
        assertEquals("#ff6347", PingColor.pingColor(200.0, false));
        assertEquals("#d3d3d3", PingColor.pingColor(null, true));
    }

    @Test
    void nodeLabelUsesAvgPing() {
        HopNode node = new HopNode(3, "192.168.0.1", 12.0, false);
        assertEquals("Hop 3\n192.168.0.1\nLAN\n12 ms", PingColor.nodeLabel(node, ip -> 12.0));
        assertEquals(
                "Hop 4\n8.8.8.8\nUS\nAS15169 Google\n5 ms",
                PingColor.nodeLabel(new HopNode(4, "8.8.8.8", 5.0, false), ip -> 5.0));
        assertEquals("Hop 2\n*", PingColor.nodeLabel(new HopNode(2, "*", null, true), ip -> null));
    }

    @Test
    void nodeLabelIncludesHopStats() {
        HopStatsSummary summary = new HopStatsSummary(3.0, 10.0);
        HopNode node = new HopNode(3, "8.8.8.8", 12.0, false);
        String label = PingColor.nodeLabel(node, ip -> 12.0, hop -> hop == 3 ? summary : null);
        assertTrue(label.contains("j:3"));
        assertTrue(label.contains("loss:10%"));
    }

    @Test
    void nodeLabelIncludesCachedRdns() {
        DnsResolver.putCache("8.8.8.8", "dns.google");
        String label = PingColor.nodeLabel(new HopNode(4, "8.8.8.8", 5.0, false), ip -> 5.0);
        assertEquals("Hop 4\n8.8.8.8\ndns.google\nUS\nAS15169 Google\n5 ms", label);
    }
}
