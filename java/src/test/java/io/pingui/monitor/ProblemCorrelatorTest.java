package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProblemCorrelator} (P29-001). */
class ProblemCorrelatorTest {
    private static final Instant T0 = Instant.parse("2026-09-03T10:00:00Z");

    @Test
    void emptyWhenFewerThanTwoHosts() {
        assertTrue(ProblemCorrelator.correlate(List.of(), 0).isEmpty());
        assertTrue(ProblemCorrelator.correlate(
                        List.of(obs("a", List.of(hop(1, "10.0.0.1")), T0)), 1)
                .isEmpty());
    }

    @Test
    void emptyWhenRoutesMissing() {
        assertTrue(ProblemCorrelator.correlate(
                        List.of(obs("a", List.of(), T0), obs("b", List.of(), T0)), 2)
                .isEmpty());
    }

    @Test
    void findsLastSharedStableAndIspScope() {
        List<HopNode> routeA = List.of(
                hop(1, "198.51.100.1"),
                hop(2, "198.51.100.10"),
                Models.timeout(3),
                Models.timeout(4));
        List<HopNode> routeB = List.of(
                hop(1, "198.51.100.1"),
                hop(2, "198.51.100.10"),
                Models.timeout(3),
                Models.timeout(4));
        List<HopNode> routeC = List.of(
                hop(1, "198.51.100.1"),
                hop(2, "198.51.100.10"),
                hop(3, "203.0.113.5"),
                Models.timeout(4));

        ProblemCorrelation result = ProblemCorrelator.correlate(
                        List.of(
                                obs("a.example", routeA, T0),
                                obs("b.example", routeB, T0.plusSeconds(30)),
                                obs("c.example", routeC, T0.plusSeconds(45))),
                        8)
                .orElseThrow();

        assertEquals(3, result.degradedHostCount());
        assertEquals(8, result.totalHostCount());
        assertEquals(Optional.of("198.51.100.10"), result.lastSharedStableHop());
        assertEquals(Optional.of(2), result.lastSharedStableHopNumber());
        assertTrue(result.firstSharedProblemHop().isEmpty());
        assertEquals(ProblemCorrelationScope.ISP, result.scope());
        assertTrue(result.timeOverlap());
        assertEquals(Duration.ofSeconds(45), result.startSpread());
    }

    @Test
    void findsFirstSharedProblemHopWhenNextIpSharedByMajority() {
        List<HopNode> routeA = List.of(hop(1, "10.0.0.1"), hop(2, "203.0.113.10"), Models.timeout(3));
        List<HopNode> routeB = List.of(hop(1, "10.0.0.1"), hop(2, "203.0.113.10"), Models.timeout(3));
        List<HopNode> routeC = List.of(hop(1, "10.0.0.1"), Models.timeout(2));

        ProblemCorrelation result = ProblemCorrelator.correlate(
                        List.of(obs("a", routeA, T0), obs("b", routeB, T0), obs("c", routeC, T0)), 3)
                .orElseThrow();

        assertEquals(Optional.of("10.0.0.1"), result.lastSharedStableHop());
        assertEquals(Optional.of("203.0.113.10"), result.firstSharedProblemHop());
        assertEquals(Optional.of(2), result.firstSharedProblemHopNumber());
        assertEquals(ProblemCorrelationScope.LOCAL, result.scope());
    }

    @Test
    void localScopeForPrivateLastStable() {
        List<HopNode> routeA = List.of(hop(1, "192.168.0.1"), Models.timeout(2));
        List<HopNode> routeB = List.of(hop(1, "192.168.0.1"), Models.timeout(2));

        ProblemCorrelation result = ProblemCorrelator.correlate(
                        List.of(obs("a", routeA, T0), obs("b", routeB, T0)), 2)
                .orElseThrow();

        assertEquals(ProblemCorrelationScope.LOCAL, result.scope());
        assertEquals(Optional.of("192.168.0.1"), result.lastSharedStableHop());
    }

    @Test
    void edgeScopeForDeepSharedPrefix() {
        List<HopNode> deep = List.of(
                hop(1, "203.0.113.1"),
                hop(2, "203.0.113.2"),
                hop(3, "203.0.113.3"),
                hop(4, "203.0.113.4"),
                Models.timeout(5));
        ProblemCorrelation result = ProblemCorrelator.correlate(
                        List.of(obs("a", deep, T0), obs("b", deep, T0)), 2)
                .orElseThrow();

        assertEquals(ProblemCorrelationScope.EDGE, result.scope());
        assertEquals(Optional.of("203.0.113.4"), result.lastSharedStableHop());
    }

    @Test
    void timeOverlapFalseWhenSpreadExceedsWindow() {
        List<HopNode> route = List.of(hop(1, "10.0.0.1"), Models.timeout(2));
        ProblemCorrelation result = ProblemCorrelator.correlate(
                        List.of(obs("a", route, T0), obs("b", route, T0.plusSeconds(180))),
                        2,
                        Duration.ofSeconds(120))
                .orElseThrow();

        assertFalse(result.timeOverlap());
        assertEquals(Duration.ofSeconds(180), result.startSpread());
    }

    @Test
    void allReachableUsesFullPathAsStablePrefix() {
        List<HopNode> routeA = List.of(hop(1, "10.0.0.1"), hop(2, "198.51.100.1"), hop(3, "8.8.8.8"));
        List<HopNode> routeB = List.of(hop(1, "10.0.0.1"), hop(2, "198.51.100.1"), hop(3, "1.1.1.1"));

        ProblemCorrelation result = ProblemCorrelator.correlate(
                        List.of(obs("a", routeA, T0), obs("b", routeB, T0)), 2)
                .orElseThrow();

        assertEquals(Optional.of("198.51.100.1"), result.lastSharedStableHop());
        assertEquals(ProblemCorrelationScope.ISP, result.scope());
    }

    private static ProblemHostObservation obs(String host, List<HopNode> route, Instant started) {
        return new ProblemHostObservation(host, route, started);
    }

    private static HopNode hop(int ttl, String ip) {
        return new HopNode(ttl, ip, 5.0, false);
    }
}
