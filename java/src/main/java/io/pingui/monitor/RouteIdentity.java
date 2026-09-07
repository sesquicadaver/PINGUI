package io.pingui.monitor;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hop-indexed route identity {@code (hop, ip|timeout)} for TRACE/MTR (P34-001).
 *
 * <p>Timeouts are first-class tokens so a transient {@code *} does not shift hop indices the way
 * {@link Models.RouteSnapshot#routeIps()} does. Topology comparison ignores hops where either side
 * is a timeout.
 */
public final class RouteIdentity {
    private final List<String> tokens;

    private RouteIdentity(List<String> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public static RouteIdentity empty() {
        return new RouteIdentity(List.of());
    }

    /** Builds identity from hop nodes (1-based indices preserved; gaps filled as timeout). */
    public static RouteIdentity fromHops(List<HopNode> hops) {
        if (hops == null || hops.isEmpty()) {
            return empty();
        }
        int maxHop = 0;
        for (HopNode hop : hops) {
            if (hop != null && hop.hop() > maxHop) {
                maxHop = hop.hop();
            }
        }
        if (maxHop < 1) {
            return empty();
        }
        List<String> tokens = new ArrayList<>(maxHop);
        for (int i = 0; i < maxHop; i++) {
            tokens.add(Models.TIMEOUT_IP);
        }
        for (HopNode hop : hops) {
            if (hop == null || hop.hop() < 1) {
                continue;
            }
            tokens.set(hop.hop() - 1, tokenFor(hop));
        }
        return new RouteIdentity(tokens);
    }

    /** Legacy seed from reachable-IP lists (HostRegistry bookmarks). */
    public static RouteIdentity fromReachableIps(List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            return empty();
        }
        List<String> tokens = new ArrayList<>(ips.size());
        for (String ip : ips) {
            if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
                tokens.add(Models.TIMEOUT_IP);
            } else {
                tokens.add(ip);
            }
        }
        return new RouteIdentity(tokens);
    }

    public boolean isEmpty() {
        return tokens.isEmpty() || reachableIps().isEmpty();
    }

    public List<String> tokens() {
        return tokens;
    }

    public List<String> reachableIps() {
        List<String> ips = new ArrayList<>();
        for (String token : tokens) {
            if (!isTimeout(token)) {
                ips.add(token);
            }
        }
        return List.copyOf(ips);
    }

    /**
     * True when no reachable hop disagrees. A timeout on either side is not a topology difference
     * (transient {@code *} ≠ new route).
     */
    public boolean sameTopology(RouteIdentity other) {
        Objects.requireNonNull(other, "other");
        int max = Math.max(tokens.size(), other.tokens.size());
        for (int i = 0; i < max; i++) {
            String a = tokenAt(i);
            String b = other.tokenAt(i);
            if (isTimeout(a) || isTimeout(b)) {
                continue;
            }
            if (!a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    /** Prefers reachable IPs from {@code other}; keeps this identity's known IPs on timeouts. */
    public RouteIdentity mergeKnown(RouteIdentity other) {
        if (other == null || other.tokens.isEmpty()) {
            return this;
        }
        if (tokens.isEmpty()) {
            return other;
        }
        int max = Math.max(tokens.size(), other.tokens.size());
        List<String> merged = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            String theirs = other.tokenAt(i);
            String ours = tokenAt(i);
            if (!isTimeout(theirs)) {
                merged.add(theirs);
            } else if (!isTimeout(ours)) {
                merged.add(ours);
            } else {
                merged.add(Models.TIMEOUT_IP);
            }
        }
        return new RouteIdentity(merged);
    }

    private String tokenAt(int zeroBased) {
        if (zeroBased < 0 || zeroBased >= tokens.size()) {
            return Models.TIMEOUT_IP;
        }
        return tokens.get(zeroBased);
    }

    private static String tokenFor(HopNode hop) {
        if (hop == null || !hop.isReachable()) {
            return Models.TIMEOUT_IP;
        }
        String ip = hop.ip();
        return ip == null || ip.isBlank() ? Models.TIMEOUT_IP : ip;
    }

    private static boolean isTimeout(String token) {
        return token == null || token.isBlank() || Models.TIMEOUT_IP.equals(token);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RouteIdentity other)) {
            return false;
        }
        return tokens.equals(other.tokens);
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }

    @Override
    public String toString() {
        return String.join("|", tokens);
    }
}
