/**
 * Offline IP label enrichment for hop display (phase 36).
 *
 * <p><b>Contract (P36-001):</b>
 *
 * <ul>
 *   <li><b>Java-only</b> for new GeoIP/MMDB work — Python remains legacy / bugfix-only.
 *   <li><b>Offline-only</b> — no HTTP, whois, or DNS during monitoring enrichment; literals via {@link
 *       io.pingui.geoip.IpLiterals} only.
 *   <li><b>No probe blocking</b> — enrichment must not hold {@code inFlight} or share locks with the
 *       probe/monitor path — use {@link io.pingui.geoip.IpMetadataService#cached(String)} / {@link
 *       io.pingui.geoip.IpMetadataService#offer(String)} (P36-006).
 *   <li>Current {@link io.pingui.geoip.GeoCountry} / {@link io.pingui.geoip.AsnLookup} are temporary
 *       <em>country/ASN hints</em> (YAML CIDR → label), not full geolocation — replaced by {@link
 *       io.pingui.geoip.IpMetadata} / {@link io.pingui.geoip.IpMetadataProvider} (P36-002+), with
 *       {@link io.pingui.geoip.YamlIpMetadataOverrides} for YAML CIDR overrides (P36-004), {@link
 *       io.pingui.geoip.MmdbIpMetadataProvider} for offline MMDB (P36-005), and {@link
 *       io.pingui.geoip.IpMetadataService} for precedence / bounded cache / atomic reload (P36-006),
 *       and {@link io.pingui.geoip.IpMetadataBootstrap} for CLI/bootstrap wiring (P36-007).
 * </ul>
 */
package io.pingui.geoip;
