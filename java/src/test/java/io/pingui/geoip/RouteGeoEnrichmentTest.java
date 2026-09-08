package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouteGeoEnrichmentTest {

    @BeforeEach
    void installOverrides() {
        YamlIpMetadataOverrides overrides = YamlIpMetadataOverrides.fromYaml(
                """
                prefixes:
                  8.8.8.8/32:
                    country: US
                    asn: 15169
                    organization: Google
                """);
        IpMetadataRuntime.install(IpMetadataService.overridesOnly(overrides));
    }

    @AfterEach
    void resetRuntime() {
        IpMetadataRuntime.close();
    }

    @Test
    void routeChangeDetailContainsGeoAndAsnDiffs() {
        String detail = RouteGeoEnrichment.routeChangeDetailJson(List.of("10.0.0.1"), List.of("8.8.8.8"));
        assertTrue(detail.contains("\"geo_diff\""));
        assertTrue(detail.contains("\"asn_diff\""));
        assertTrue(detail.contains("\"US\""));
        assertTrue(detail.contains("15169"));
    }

    @Test
    void appendRouteChangeDiffsKeepsBaseFields() {
        String base =
                "{\"event\":\"route_change\",\"host\":\"h\",\"old_ips\":[],\"new_ips\":[\"8.8.8.8\"],\"timestamp\":\"2026-09-08T00:00:00Z\",\"profile\":\"default\"}";
        String enriched = RouteGeoEnrichment.appendRouteChangeDiffs(base, List.of(), List.of("8.8.8.8"));
        assertTrue(enriched.startsWith("{\"event\":\"route_change\""));
        assertTrue(enriched.contains("\"host\":\"h\""));
        assertTrue(enriched.contains("\"geo_diff\""));
        assertTrue(enriched.endsWith("}"));
    }

    @Test
    void hopGeoFieldsAreNullableStableKeys() {
        StringBuilder sb = new StringBuilder();
        RouteGeoEnrichment.appendHopGeoFields(sb, RouteGeoEnrichment.resolveLiteral("8.8.8.8"));
        String fields = sb.toString();
        assertTrue(fields.contains("\"country_iso\":\"US\""));
        assertTrue(fields.contains("\"asn\":15169"));
        assertTrue(fields.contains("\"source\":\"OVERRIDE\""));
        assertTrue(fields.contains("\"scope\":\"PUBLIC\""));
    }

    @Test
    void csvGeoCellsForUnknownLiteral() {
        String[] cells = RouteGeoEnrichment.csvGeoCells(RouteGeoEnrichment.resolveLiteral("*"));
        assertEquals(4, cells.length);
        assertEquals("", cells[0]);
        assertEquals("", cells[1]);
    }

    @Test
    void disabledServiceStillEmitsDiffKeys() {
        IpMetadataRuntime.close();
        String detail = RouteGeoEnrichment.routeChangeDetailJson(List.of("8.8.8.8"), List.of("1.1.1.1"));
        assertTrue(detail.contains("\"geo_diff\""));
        assertFalse(detail.contains("\"US\""));
    }
}
