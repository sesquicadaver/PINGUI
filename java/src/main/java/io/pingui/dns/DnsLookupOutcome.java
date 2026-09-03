package io.pingui.dns;

/**
 * Result class of a forward DNS lookup for hostname control (P29-004).
 *
 * <p>Mapped to persistence {@code dns_change.state} when the observation is stored (errors and
 * address-set changes are distinct events; never auto-incidents).
 */
public enum DnsLookupOutcome {
    OK("ok"),
    NXDOMAIN("nxdomain"),
    TIMEOUT("timeout"),
    SERVFAIL("servfail"),
    ERROR("error");

    private final String id;

    DnsLookupOutcome(String id) {
        this.id = id;
    }

    /** Wire / SQLite state id (lowercase). */
    public String id() {
        return id;
    }
}
