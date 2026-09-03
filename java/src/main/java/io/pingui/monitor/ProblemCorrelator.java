package io.pingui.monitor;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Correlates concurrent host degradations using existing route hops (no new probes) — P29-001.
 *
 * <p>Finds the longest common stable hop prefix, the first shared problem hop after it, a scope
 * hint ({@link ProblemCorrelationScope}), and whether problem start times overlap.
 */
public final class ProblemCorrelator {
    /** Max start-time spread still considered concurrent degradation. */
    public static final Duration DEFAULT_OVERLAP_WINDOW = Duration.ofSeconds(120);

    private ProblemCorrelator() {}

    /** Correlates with {@link #DEFAULT_OVERLAP_WINDOW}. */
    public static Optional<ProblemCorrelation> correlate(
            List<ProblemHostObservation> observations, int totalHostCount) {
        return correlate(observations, totalHostCount, DEFAULT_OVERLAP_WINDOW);
    }

    /**
     * @param observations degraded hosts with current routes and problem start times
     * @param totalHostCount session host count (for "N of M" messaging)
     * @param overlapWindow max start spread for {@link ProblemCorrelation#timeOverlap()}
     * @return empty when fewer than two usable observations
     */
    public static Optional<ProblemCorrelation> correlate(
            List<ProblemHostObservation> observations, int totalHostCount, Duration overlapWindow) {
        if (observations == null || observations.size() < 2) {
            return Optional.empty();
        }
        List<ProblemHostObservation> usable = observations.stream()
                .filter(o -> o.route() != null && !o.route().isEmpty())
                .toList();
        if (usable.size() < 2) {
            return Optional.empty();
        }
        Duration window = overlapWindow == null || overlapWindow.isNegative() ? DEFAULT_OVERLAP_WINDOW : overlapWindow;
        int total = Math.max(totalHostCount, usable.size());

        List<List<String>> stablePrefixes =
                usable.stream().map(ProblemCorrelator::stablePrefixIps).toList();
        List<String> commonPrefix = longestCommonPrefix(stablePrefixes);
        Optional<String> lastStable =
                commonPrefix.isEmpty() ? Optional.empty() : Optional.of(commonPrefix.get(commonPrefix.size() - 1));
        Optional<Integer> lastStableHopNumber =
                lastStable.isPresent() ? Optional.of(commonPrefix.size()) : Optional.empty();

        int problemIndex = commonPrefix.size();
        Optional<String> firstProblem = firstSharedProblemHop(usable, problemIndex);
        Optional<Integer> firstProblemHopNumber =
                firstProblem.isPresent() ? Optional.of(problemIndex + 1) : Optional.empty();

        ProblemCorrelationScope scope = classifyScope(commonPrefix, lastStable);
        Duration spread = startSpread(usable);
        boolean overlap = !spread.isNegative() && spread.compareTo(window) <= 0;

        List<String> hosts =
                usable.stream().map(ProblemHostObservation::host).sorted().toList();
        return Optional.of(new ProblemCorrelation(
                usable.size(),
                total,
                hosts,
                lastStable,
                lastStableHopNumber,
                firstProblem,
                firstProblemHopNumber,
                scope,
                overlap,
                spread));
    }

    /** Reachable hops before the first timeout / unreachable hop (all-reachable → full path). */
    static List<String> stablePrefixIps(ProblemHostObservation observation) {
        List<HopNode> nodes = observation.route();
        int end = firstProblemIndex(nodes);
        List<String> ips = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            ips.add(nodes.get(i).ip());
        }
        return List.copyOf(ips);
    }

    /** Index of first unreachable hop, or {@code nodes.size()} when the path is fully reachable. */
    static int firstProblemIndex(List<HopNode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            if (!nodes.get(i).isReachable()) {
                return i;
            }
        }
        return nodes.size();
    }

    static List<String> longestCommonPrefix(List<List<String>> sequences) {
        if (sequences.isEmpty()) {
            return List.of();
        }
        List<String> prefix = new ArrayList<>(sequences.get(0));
        for (int i = 1; i < sequences.size(); i++) {
            List<String> other = sequences.get(i);
            int limit = Math.min(prefix.size(), other.size());
            int shared = 0;
            while (shared < limit && prefix.get(shared).equals(other.get(shared))) {
                shared++;
            }
            if (shared < prefix.size()) {
                prefix = new ArrayList<>(prefix.subList(0, shared));
            }
            if (prefix.isEmpty()) {
                return List.of();
            }
        }
        return List.copyOf(prefix);
    }

    static Optional<String> firstSharedProblemHop(List<ProblemHostObservation> usable, int index) {
        Map<String, Integer> counts = new HashMap<>();
        for (ProblemHostObservation observation : usable) {
            List<HopNode> nodes = observation.route();
            if (nodes.size() <= index) {
                continue;
            }
            HopNode node = nodes.get(index);
            if (!node.isReachable() || Models.TIMEOUT_IP.equals(node.ip())) {
                continue;
            }
            counts.merge(node.ip(), 1, Integer::sum);
        }
        int threshold = Math.max(2, (usable.size() + 1) / 2);
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey);
    }

    static ProblemCorrelationScope classifyScope(List<String> commonPrefix, Optional<String> lastStable) {
        if (commonPrefix.isEmpty() || lastStable.isEmpty()) {
            return ProblemCorrelationScope.UNKNOWN;
        }
        String hop = lastStable.get();
        if (isPrivateOrLan(hop)) {
            return ProblemCorrelationScope.LOCAL;
        }
        int hopNumber = commonPrefix.size();
        if (hopNumber <= 1) {
            return ProblemCorrelationScope.LOCAL;
        }
        if (hopNumber <= 3) {
            return ProblemCorrelationScope.ISP;
        }
        return ProblemCorrelationScope.EDGE;
    }

    static boolean isPrivateOrLan(String ip) {
        if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip.trim());
            return address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isAnyLocalAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    static Duration startSpread(List<ProblemHostObservation> usable) {
        Instant min = null;
        Instant max = null;
        for (ProblemHostObservation observation : usable) {
            Instant started = observation.problemStartedAt();
            if (min == null || started.isBefore(min)) {
                min = started;
            }
            if (max == null || started.isAfter(max)) {
                max = started;
            }
        }
        if (min == null || max == null) {
            return Duration.ZERO;
        }
        Duration spread = Duration.between(min, max);
        return spread.isNegative() ? Duration.ZERO : spread;
    }
}
