/**
 * Offline IP label enrichment for hop display (phase 36).
 *
 * <p><b>Contract (P36-001…P36-012):</b>
 *
 * <ul>
 *   <li><b>Java-only</b> for new GeoIP/MMDB work — Python remains legacy / bugfix-only.
 *   <li><b>Offline-only</b> — no HTTP, whois, or DNS during monitoring enrichment; literals via {@link
 *       io.pingui.geoip.IpLiterals} only.
 *   <li><b>No probe blocking</b> — enrichment must not hold {@code inFlight} or share locks with the
 *       probe/monitor path — use {@link io.pingui.geoip.IpMetadataService#cached(String)} / {@link
 *       io.pingui.geoip.IpMetadataService#offer(String)} (P36-006).
 *   <li>Country/ASN enrichment is {@link io.pingui.geoip.IpMetadata} via {@link
 *       io.pingui.geoip.IpMetadataProvider}: {@link io.pingui.geoip.YamlIpMetadataOverrides} (P36-004),
 *       {@link io.pingui.geoip.MergingIpMetadataProvider} for geo+ASN hints (P36-012), {@link
 *       io.pingui.geoip.MmdbIpMetadataProvider} for offline MMDB (P36-005), {@link
 *       io.pingui.geoip.IpMetadataService} for precedence / bounded cache / atomic reload (P36-006),
 *       and {@link io.pingui.geoip.IpMetadataBootstrap} for CLI/bootstrap wiring (P36-007). Legacy
 *       parallel {@code GeoCountry}/{@code AsnLookup}/{@code AsnInfo} removed in P36-012.
 *   <li>GUI labels use cache-only {@link io.pingui.ui.HopGeoLabels} / {@link io.pingui.ui.PingColor}
 *       (P36-008). Event/API/export enrichment uses {@link io.pingui.geoip.RouteGeoEnrichment}
 *       (P36-009). Operator surfaces export {@link io.pingui.geoip.GeoIpOpsStats} on {@code /ops},
 *       Prometheus, and App Status (P36-010). Hard lookup timeout + probe-path proofs (P36-011).
 * </ul>
 */
package io.pingui.geoip;
