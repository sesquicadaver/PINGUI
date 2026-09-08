package io.pingui.geoip;

/** Address scope for offline IP metadata (P36-002). Classification details land in P36-003. */
public enum IpAddressScope {
    /** Globally routable unicast (or unknown-but-not-special). */
    PUBLIC,
    /** RFC1918 / ULA / site-local style private use. */
    PRIVATE,
    /** Loopback, link-local, CGNAT, documentation, multicast, unspecified, etc. */
    SPECIAL
}
