package io.pingui.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Resolves the operator machine LAN IPv4 for session-DB auto-naming (P22-005 /
 * ADR_HOST_PROBLEM_INDICATOR).
 */
public final class LocalIpv4 {
    public static final String FALLBACK = "unknown";

    private LocalIpv4() {}

    /** Prefer RFC1918 IPv4; else first non-loopback IPv4; else {@link #FALLBACK}. */
    public static String resolveOperatorLan() {
        return resolveOperatorLan(LocalIpv4::listInterfaceAddresses);
    }

    /** Test hook with injectable address enumeration. */
    static String resolveOperatorLan(Supplier<List<InetAddress>> addresses) {
        Objects.requireNonNull(addresses, "addresses");
        List<String> rfc1918 = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (InetAddress address : addresses.get()) {
            if (!(address instanceof Inet4Address) || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                continue;
            }
            String host = address.getHostAddress();
            if (host == null || host.isBlank()) {
                continue;
            }
            if (isRfc1918(host)) {
                rfc1918.add(host);
            } else {
                other.add(host);
            }
        }
        if (!rfc1918.isEmpty()) {
            return rfc1918.get(0);
        }
        if (!other.isEmpty()) {
            return other.get(0);
        }
        return FALLBACK;
    }

    /** Dots/colons → {@code -} for safe filenames. */
    public static String sanitizeForFilename(String ip) {
        if (ip == null || ip.isBlank()) {
            return FALLBACK;
        }
        return ip.strip().toLowerCase(Locale.ROOT).replace('.', '-').replace(':', '-');
    }

    static boolean isRfc1918(String ipv4) {
        String[] parts = ipv4.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 10) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            return a == 172 && b >= 16 && b <= 31;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static List<InetAddress> listInterfaceAddresses() {
        List<InetAddress> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return List.of();
            }
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                out.addAll(Collections.list(nif.getInetAddresses()));
            }
        } catch (SocketException ignored) {
            return List.of();
        }
        return out;
    }
}
