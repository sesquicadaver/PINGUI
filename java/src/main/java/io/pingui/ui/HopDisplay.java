package io.pingui.ui;

import io.pingui.model.Models;

/** Display helpers for hop IP addresses in graph labels. */
public final class HopDisplay {
    private HopDisplay() {}

    /**
     * Format hop IP for graph labels. IPv6 literals are wrapped in {@code [...]} (RFC 4007
     * convention) so long addresses remain readable in monospace boxes.
     */
    public static String formatHopIp(String ip) {
        if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
            return ip;
        }
        if (ip.indexOf(':') >= 0 && !(ip.startsWith("[") && ip.endsWith("]"))) {
            return "[" + ip + "]";
        }
        return ip;
    }
}
