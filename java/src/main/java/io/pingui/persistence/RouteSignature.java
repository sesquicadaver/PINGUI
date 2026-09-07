package io.pingui.persistence;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Canonical hop-indexed topology signature for deduplicated {@code route} rows (P30-004 / P34-001 /
 * P35-009).
 *
 * <p>Format: {@code 1=10.0.0.1|2=172.16.4.1|3=8.8.8.8}. Timeout / unreachable hops are omitted so a
 * transient {@code *} does not create a distinct deduplicated route.
 *
 * <p>P35-009: {@link #stabilize(List, Map)} is the single hop-indexed source for both {@code
 * signature} and persisted {@code hops_json}. Prefer {@link #fromHops(List)} on the stabilized
 * chain — do not rebuild the signature from a wider {@code lastKnown} map than the persisted hops.
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
     *
     * <p>Only hop indices present in {@code hops} (or filled onto those slots) participate. Extra
     * keys in {@code lastKnownByHop} beyond the snapshot are ignored here — use {@link #stabilize}
     * first when the persisted chain must include those hops (P35-009).
     */
    public static String fromHops(List<HopNode> hops, Map<Integer, String> lastKnownByHop) {
        Objects.requireNonNull(hops, "hops");
        Map<Integer, String> known = lastKnownByHop == null ? Map.of() : lastKnownByHop;
        if (hops.isEmpty()) {
            return "";
        }
        Map<Integer, String> byHop = new HashMap<>();
        for (HopNode hop : hops) {
            if (hop == null || hop.hop() < 1) {
                continue;
            }
            if (hop.isReachable()) {
                String ip = hop.ip();
                if (ip != null && !ip.isBlank() && !Models.TIMEOUT_IP.equals(ip)) {
                    byHop.put(hop.hop(), ip);
                }
                continue;
            }
            String knownIp = known.get(hop.hop());
            if (knownIp != null && !knownIp.isBlank() && !Models.TIMEOUT_IP.equals(knownIp)) {
                byHop.put(hop.hop(), knownIp);
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

    /**
     * Single hop-indexed representation for signature + {@code hops_json} (P35-009).
     *
     * <p>Union of snapshot hop indices and last-known reachable IPs. Timeout slots in the snapshot
     * are filled from last-known; last-known hops beyond a short snapshot are included so signature
     * and persisted JSON cannot diverge.
     */
    public static List<HopNode> stabilize(List<HopNode> hops, Map<Integer, String> lastKnownByHop) {
        Objects.requireNonNull(hops, "hops");
        Map<Integer, String> known = lastKnownByHop == null ? Map.of() : lastKnownByHop;
        Map<Integer, HopNode> snapshotByHop = new HashMap<>();
        for (HopNode hop : hops) {
            if (hop == null || hop.hop() < 1) {
                continue;
            }
            snapshotByHop.put(hop.hop(), hop);
        }
        TreeSet<Integer> indices = new TreeSet<>(snapshotByHop.keySet());
        for (Map.Entry<Integer, String> entry : known.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 1) {
                continue;
            }
            String ip = entry.getValue();
            if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
                continue;
            }
            indices.add(entry.getKey());
        }
        if (indices.isEmpty()) {
            return List.of();
        }
        List<HopNode> out = new ArrayList<>(indices.size());
        for (Integer hopIndex : indices) {
            HopNode snap = snapshotByHop.get(hopIndex);
            if (snap != null && snap.isReachable()) {
                String ip = snap.ip();
                if (ip != null && !ip.isBlank() && !Models.TIMEOUT_IP.equals(ip)) {
                    out.add(snap);
                    continue;
                }
            }
            String knownIp = known.get(hopIndex);
            if (knownIp != null && !knownIp.isBlank() && !Models.TIMEOUT_IP.equals(knownIp)) {
                out.add(new HopNode(hopIndex, knownIp, null, false));
                continue;
            }
            if (snap != null) {
                out.add(snap);
            }
        }
        return List.copyOf(out);
    }
}
