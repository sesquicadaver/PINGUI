package io.pingui.persistence;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Canonical hop-IP signature for deduplicated {@code route} rows (P30-004).
 *
 * <p>Format: {@code 10.0.0.1|172.16.4.1|*|8.8.8.8} — unreachable / timeout hops are {@code *}.
 */
public final class RouteSignature {
    private RouteSignature() {}

    /** Builds signature from hop nodes; empty list → empty string. */
    public static String fromHops(List<HopNode> hops) {
        Objects.requireNonNull(hops, "hops");
        if (hops.isEmpty()) {
            return "";
        }
        return hops.stream().map(RouteSignature::token).collect(Collectors.joining("|"));
    }

    private static String token(HopNode hop) {
        if (hop == null || !hop.isReachable()) {
            return Models.TIMEOUT_IP;
        }
        String ip = hop.ip();
        return ip == null || ip.isBlank() ? Models.TIMEOUT_IP : ip;
    }
}
