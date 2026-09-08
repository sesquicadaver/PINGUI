package io.pingui.geoip;

/**
 * Offline IP metadata lookup contract (P36-002).
 *
 * <p>Implementations must:
 *
 * <ul>
 *   <li>accept IPv4 and IPv6 <em>literals</em> only (via {@link IpLiterals}) — no DNS;
 *   <li>perform no HTTP/whois;
 *   <li>not block the probe/monitor critical path with remote I/O;
 *   <li>not mutate global application state as a side effect of lookup;
 *   <li>not invent coordinates (use {@link IpMetadataSource#NONE} when unknown).
 * </ul>
 */
@FunctionalInterface
public interface IpMetadataProvider {

    /**
     * Resolve metadata for an IP literal.
     *
     * @param ip raw hop/host address string (may include brackets for IPv6)
     * @return enrichment snapshot, or {@code null} when {@code ip} is not a parseable address
     *     literal (hostname / blank / invalid)
     */
    IpMetadata lookup(String ip);
}
