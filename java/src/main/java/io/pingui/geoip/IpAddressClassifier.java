package io.pingui.geoip;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Offline classification of IP literals into {@link IpAddressScope} (P36-003).
 *
 * <p>Does not perform DNS. Documentation, CGNAT, multicast, and other special ranges never receive
 * a country from enrichment pipelines that honor this classifier.
 */
public final class IpAddressClassifier {
    private IpAddressClassifier() {}

    /**
     * @return scope for a parsed address; never {@code null}
     */
    public static IpAddressScope scopeOf(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("address is required");
        }
        if (address instanceof Inet4Address ipv4) {
            return scopeOfV4(ipv4ToInt(ipv4));
        }
        if (address instanceof Inet6Address ipv6) {
            return scopeOfV6(ipv6);
        }
        throw new IllegalArgumentException("Unsupported address type: " + address.getClass());
    }

    /**
     * @return scope for an IP literal, or {@code null} when {@code raw} is not a literal
     */
    public static IpAddressScope scopeOfLiteral(String raw) {
        InetAddress parsed = IpLiterals.parseLiteralOrNull(raw);
        return parsed == null ? null : scopeOf(parsed);
    }

    private static IpAddressScope scopeOfV4(int ip) {
        // 0.0.0.0/8 — this-network / unspecified
        if (inCidr(ip, 0x00000000, 8)) {
            return IpAddressScope.SPECIAL;
        }
        // 10.0.0.0/8 — RFC1918
        if (inCidr(ip, 0x0a000000, 8)) {
            return IpAddressScope.PRIVATE;
        }
        // 100.64.0.0/10 — CGNAT (RFC6598)
        if (inCidr(ip, 0x64400000, 10)) {
            return IpAddressScope.SPECIAL;
        }
        // 127.0.0.0/8 — loopback
        if (inCidr(ip, 0x7f000000, 8)) {
            return IpAddressScope.SPECIAL;
        }
        // 169.254.0.0/16 — link-local
        if (inCidr(ip, 0xa9fe0000, 16)) {
            return IpAddressScope.SPECIAL;
        }
        // 172.16.0.0/12 — RFC1918
        if (inCidr(ip, 0xac100000, 12)) {
            return IpAddressScope.PRIVATE;
        }
        // 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 — documentation (TEST-NET)
        if (inCidr(ip, 0xc0000200, 24) || inCidr(ip, 0xc6336400, 24) || inCidr(ip, 0xcb007100, 24)) {
            return IpAddressScope.SPECIAL;
        }
        // 192.168.0.0/16 — RFC1918
        if (inCidr(ip, 0xc0a80000, 16)) {
            return IpAddressScope.PRIVATE;
        }
        // 224.0.0.0/4 multicast, 240.0.0.0/4 reserved
        if (inCidr(ip, 0xe0000000, 4) || inCidr(ip, 0xf0000000, 4)) {
            return IpAddressScope.SPECIAL;
        }
        return IpAddressScope.PUBLIC;
    }

    private static IpAddressScope scopeOfV6(Inet6Address addr) {
        byte[] octets = addr.getAddress();
        if (addr.isIPv4CompatibleAddress() || isIpv4Mapped(octets)) {
            int embedded = ((octets[12] & 0xff) << 24)
                    | ((octets[13] & 0xff) << 16)
                    | ((octets[14] & 0xff) << 8)
                    | (octets[15] & 0xff);
            return scopeOfV4(embedded);
        }
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
            return IpAddressScope.SPECIAL;
        }
        if (addr.isMulticastAddress()) {
            return IpAddressScope.SPECIAL;
        }
        // fc00::/7 — ULA
        if ((octets[0] & (byte) 0xfe) == (byte) 0xfc) {
            return IpAddressScope.PRIVATE;
        }
        // fec0::/10 — deprecated site-local
        if ((octets[0] & (byte) 0xff) == (byte) 0xfe && (octets[1] & (byte) 0xc0) == (byte) 0xc0) {
            return IpAddressScope.SPECIAL;
        }
        // 2001:db8::/32 — documentation
        if ((octets[0] & 0xff) == 0x20
                && (octets[1] & 0xff) == 0x01
                && (octets[2] & 0xff) == 0x0d
                && (octets[3] & 0xff) == 0xb8) {
            return IpAddressScope.SPECIAL;
        }
        return IpAddressScope.PUBLIC;
    }

    private static boolean isIpv4Mapped(byte[] octets) {
        for (int i = 0; i < 10; i++) {
            if (octets[i] != 0) {
                return false;
            }
        }
        return (octets[10] & 0xff) == 0xff && (octets[11] & 0xff) == 0xff;
    }

    private static boolean inCidr(int ip, int network, int prefixBits) {
        if (prefixBits <= 0) {
            return true;
        }
        if (prefixBits >= 32) {
            return ip == network;
        }
        int mask = -1 << (32 - prefixBits);
        return (ip & mask) == (network & mask);
    }

    private static int ipv4ToInt(Inet4Address addr) {
        byte[] octets = addr.getAddress();
        return ((octets[0] & 0xff) << 24) | ((octets[1] & 0xff) << 16) | ((octets[2] & 0xff) << 8) | (octets[3] & 0xff);
    }
}
