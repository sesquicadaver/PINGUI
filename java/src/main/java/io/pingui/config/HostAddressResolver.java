package io.pingui.config;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** DNS resolution for ping targets by address family (V6-055). */
public final class HostAddressResolver {
    private HostAddressResolver() {}

    /**
     * Returns a ping-ready address string: literals unchanged; hostnames resolved to A or AAAA.
     *
     * @param entry host entry as configured (literal or hostname)
     * @param ipv6 when true, resolve/select IPv6 (AAAA); otherwise IPv4 (A)
     */
    public static String resolveForPing(String entry, boolean ipv6) {
        String normalized = HostAddressParser.normalize(entry);
        HostAddressKind kind = HostAddressParser.kindOf(normalized);
        if (kind == HostAddressKind.IPV4) {
            if (ipv6) {
                throw new ConfigError("Expert ping -6 cannot be used with IPv4 target: " + entry);
            }
            return normalized;
        }
        if (kind == HostAddressKind.IPV6) {
            if (!ipv6) {
                throw new ConfigError("Expert ping -4 cannot be used with IPv6 target: " + entry);
            }
            return normalized;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalized);
            for (InetAddress address : addresses) {
                if (ipv6 && address instanceof Inet6Address inet6) {
                    return HostAddressParser.normalize(stripZone(inet6.getHostAddress()));
                }
                if (!ipv6 && address instanceof Inet4Address inet4) {
                    return inet4.getHostAddress();
                }
            }
            throw new ConfigError("No " + (ipv6 ? "IPv6" : "IPv4") + " address for host: " + entry);
        } catch (UnknownHostException ex) {
            throw new ConfigError("Cannot resolve host " + entry + ": " + ex.getMessage());
        }
    }

    private static String stripZone(String hostAddress) {
        int zone = hostAddress.indexOf('%');
        return zone >= 0 ? hostAddress.substring(0, zone) : hostAddress;
    }
}
