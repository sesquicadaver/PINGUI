package io.pingui.config;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes IPv4/IPv6 literals and hostnames for config and duplicate detection. */
public final class HostAddressParser {
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+$");

    private HostAddressParser() {}

    public static HostAddressKind kindOf(String normalized) {
        if (isIpv4Literal(normalized)) {
            return HostAddressKind.IPV4;
        }
        if (normalized.indexOf(':') >= 0) {
            return HostAddressKind.IPV6;
        }
        return HostAddressKind.HOSTNAME;
    }

    /** Returns RFC 5952-style IPv6, unchanged IPv4, or stripped hostname. */
    public static String normalize(String entry) {
        if (entry == null || entry.isBlank()) {
            throw new ConfigError("Invalid host entry: '" + entry + "'");
        }
        String host = stripBrackets(entry.strip());
        if (host.isBlank()) {
            throw new ConfigError("Invalid host entry: '" + entry + "'");
        }
        if (host.contains(":")) {
            return normalizeIpv6Literal(host, entry);
        }
        if (isIpv4Literal(host)) {
            return host;
        }
        if (!HOSTNAME_PATTERN.matcher(host).matches() || host.length() > 253) {
            throw new ConfigError("Invalid host entry: '" + entry + "'");
        }
        return host;
    }

    /** Case-insensitive duplicate key (canonical for IPv6). */
    public static String duplicateKey(String normalized) {
        HostAddressKind kind = kindOf(normalized);
        return switch (kind) {
            case IPV6 -> normalized.toLowerCase(Locale.ROOT);
            case HOSTNAME -> normalized.toLowerCase(Locale.ROOT);
            case IPV4 -> normalized;
        };
    }

    private static String stripBrackets(String value) {
        if (value.startsWith("[") && value.endsWith("]") && value.length() >= 2) {
            return value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    private static String normalizeIpv6Literal(String host, String originalEntry) {
        if (host.contains("%")) {
            throw new ConfigError("IPv6 zone identifiers are not supported: '" + originalEntry + "'");
        }
        try {
            InetAddress parsed = InetAddress.getByName(host);
            if (!(parsed instanceof Inet6Address ipv6)) {
                throw new ConfigError("Invalid IPv6 address: '" + originalEntry + "'");
            }
            return formatRfc5952(ipv6.getAddress());
        } catch (UnknownHostException ex) {
            throw new ConfigError("Invalid IPv6 address: '" + originalEntry + "'");
        }
    }

    /** RFC 5952 canonical text (lowercase, longest :: zero run). */
    static String formatRfc5952(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("IPv6 address must be 16 bytes");
        }
        int[] hextets = new int[8];
        for (int i = 0; i < 8; i++) {
            hextets[i] = ((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF);
        }
        int compressStart = -1;
        int compressLen = 0;
        for (int i = 0; i < 8; ) {
            if (hextets[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && hextets[i] == 0) {
                i++;
            }
            int len = i - start;
            if (len > compressLen) {
                compressLen = len;
                compressStart = start;
            }
        }
        if (compressLen < 2) {
            compressStart = -1;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == compressStart) {
                out.append("::");
                i += compressLen - 1;
                continue;
            }
            if (!out.isEmpty() && out.charAt(out.length() - 1) != ':') {
                out.append(':');
            }
            out.append(Integer.toHexString(hextets[i]));
        }
        return out.toString();
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
