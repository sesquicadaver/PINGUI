package io.pingui.monitor;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of correlating concurrent host degradations against shared route hops (P29-001).
 */
public record ProblemCorrelation(
        int degradedHostCount,
        int totalHostCount,
        List<String> hosts,
        Optional<String> lastSharedStableHop,
        Optional<Integer> lastSharedStableHopNumber,
        Optional<String> firstSharedProblemHop,
        Optional<Integer> firstSharedProblemHopNumber,
        ProblemCorrelationScope scope,
        boolean timeOverlap,
        Duration startSpread) {
    public ProblemCorrelation {
        if (degradedHostCount < 2) {
            throw new IllegalArgumentException("degradedHostCount must be >= 2");
        }
        if (totalHostCount < degradedHostCount) {
            throw new IllegalArgumentException("totalHostCount must be >= degradedHostCount");
        }
        hosts = List.copyOf(Objects.requireNonNull(hosts, "hosts"));
        if (hosts.size() != degradedHostCount) {
            throw new IllegalArgumentException("hosts size must equal degradedHostCount");
        }
        lastSharedStableHop = lastSharedStableHop == null ? Optional.empty() : lastSharedStableHop;
        lastSharedStableHopNumber = lastSharedStableHopNumber == null ? Optional.empty() : lastSharedStableHopNumber;
        firstSharedProblemHop = firstSharedProblemHop == null ? Optional.empty() : firstSharedProblemHop;
        firstSharedProblemHopNumber =
                firstSharedProblemHopNumber == null ? Optional.empty() : firstSharedProblemHopNumber;
        Objects.requireNonNull(scope, "scope");
        startSpread = startSpread == null || startSpread.isNegative() ? Duration.ZERO : startSpread;
    }
}
