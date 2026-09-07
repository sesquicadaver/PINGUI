package io.pingui.persistence;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Canonical hop-indexed topology signature for deduplicated {@code route} rows (P30-004 / P34-001).
 *
 * <p>Format: {@code 1=10.0.0.1|2=172.16.4.1|3=8.8.8.8}. Timeout / unreachable hops are omitted so a
 * transient {@code *} does not create a distinct deduplicated route. Prefer {@link
 * #fromHops(List, Map)} with last-known IPs when filling gaps.
 */
public final class RouteSignature {
    private RouteSignature() {}

    /** Builds signature from hop nodes; empty / all-timeout → empty string. */
    public static String fromHops(List<HopNode> hops) {
        return fromHops(hops, Map.of());
    }

    /**
     * Builds signature, filling timeout positions from {@code lastKnownByHop} (1-based hop → IP)
     * so transient loss does not change topology identity.
     */
    public static String fromHops(List<HopNode> hops, Map<Integer, String> lastKnownByHop) {
        Objects.requireNonNull(hops, "hops");
        Map<Integer, String> known = lastKnownByHop == null ? Map.of() : lastKnownByHop;
        if (hops.isEmpty() && known.isEmpty()) {
            return "";
        }
        Map<Integer, String> byHop = new HashMap<>();
        for (Map.Entry<Integer, String> entry : known.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 1) {
                continue;
            }
            String ip = entry.getValue();
            if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
                continue;
            }
            byHop.put(entry.getKey(), ip);
        }
        for (HopNode hop : hops) {
            if (hop == null || hop.hop() < 1) {
                continue;
            }
            if (hop.isReachable()) {
                String ip = hop.ip();
                if (ip != null && !ip.isBlank() && !Models.TIMEOUT_IP.equals(ip)) {
                    byHop.put(hop.hop(), ip);
                }
            }
        }
        if (byHop.isEmpty()) {
            return "";
        }
        return byHop.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
    }

    /** Last-known reachable IPs keyed by 1-based hop index. */
    public static Map<Integer, String> knownIpsByHop(List<HopNode> hops) {
        if (hops == null || hops.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> known = new HashMap<>();
        for (HopNode hop : hops) {
            if (hop == null || hop.hop() < 1 || !hop.isReachable()) {
                continue;
            }
            String ip = hop.ip();
            if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
                continue;
            }
            known.put(hop.hop(), ip);
        }
        return Map.copyOf(known);
    }

    /** Stabilizes hops for signature/persist: timeout slots filled from last-known IPs. */
    public static List<HopNode> stabilize(List<HopNode> hops, Map<Integer, String> lastKnownByHop) {
        Objects.requireNonNull(hops, "hops");
        if (hops.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> known = lastKnownByHop == null ? Map.of() : lastKnownByHop;
        List<HopNode> out = new ArrayList<>(hops.size());
        for (HopNode hop : hops) {
            if (hop == null) {
                continue;
            }
            if (hop.isReachable()) {
                out.add(hop);
                continue;
            }
            String knownIp = known.get(hop.hop());
            if (knownIp != null && !knownIp.isBlank() && !Models.TIMEOUT_IP.equals(knownIp)) {
                out.add(new HopNode(hop.hop(), knownIp, null, false));
            } else {
                out.add(hop);
            }
        }
        return List.copyOf(out);
    }
}
