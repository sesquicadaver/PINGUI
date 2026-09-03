package io.pingui.dns;

import java.net.InetAddress;

/**
 * Injectable forward DNS (A/AAAA) resolution for hostname control and tests (P29-004).
 */
@FunctionalInterface
public interface ForwardDnsLookup {
    /**
     * Resolves {@code hostname} to all addresses (v4 and/or v6).
     *
     * @throws Exception on NXDOMAIN, timeout, SERVFAIL, or other resolver failure
     */
    InetAddress[] resolve(String hostname) throws Exception;
}
