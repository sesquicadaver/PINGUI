package io.pingui.geoip;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Parses IPv4/IPv6 address literals only — never performs DNS for hostnames.
 *
 * <p>Used by offline GeoIP/ASN label lookups so hop labels stay network-free.
 */
public final class IpLiterals {
    private IpLiterals() {}

    /**
     * @return parsed literal address, or {@code null} when {@code raw} is blank, a hostname, or
     *     invalid
     */
    public static InetAddress parseLiteralOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String host = stripBrackets(raw.strip());
        if (host.isBlank() || host.contains("%")) {
            return null;
        }
        if (isIpv4Literal(host)) {
            try {
                InetAddress parsed = InetAddress.getByName(host);
                return parsed instanceof Inet4Address ? parsed : null;
            } catch (UnknownHostException ex) {
                return null;
            }
        }
        if (host.indexOf(':') < 0) {
            // Hostname or other non-literal — do not resolve via DNS.
            return null;
        }
        try {
            InetAddress parsed = InetAddress.getByName(host);
            return parsed instanceof Inet6Address ? parsed : null;
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static String stripBrackets(String value) {
        if (value.startsWith("[") && value.endsWith("]") && value.length() >= 2) {
            return value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    private static boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
