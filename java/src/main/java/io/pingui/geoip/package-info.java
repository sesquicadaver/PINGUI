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
 *       probe/monitor path (bounded async service lands in P36-006+; until then lookups stay
 *       synchronous and local only).
 *   <li>Current {@link io.pingui.geoip.GeoCountry} / {@link io.pingui.geoip.AsnLookup} are temporary
 *       <em>country/ASN hints</em> (YAML CIDR → label), not full geolocation — replaced by {@link
 *       io.pingui.geoip.IpMetadata} / {@link io.pingui.geoip.IpMetadataProvider} (P36-002+).
 * </ul>
 */
package io.pingui.geoip;
