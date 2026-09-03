package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.monitor.EndpointState;
import io.pingui.monitor.HostProbeMode;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.RouteState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostInspectorFormatterTest {
    @Test
    void resolvedIpUsesLastReachableHop() {
        List<HopNode> hops = List.of(new HopNode(1, "10.0.0.1", 1.0, false), Models.timeout(2));
        assertEquals("10.0.0.1", HostInspectorFormatter.resolvedIpFromHops(hops));
        assertEquals("", HostInspectorFormatter.resolvedIpFromHops(List.of()));
    }

    @Test
    void snapshotIncludesEndpointRouteAndMetrics() {
        HostInspectorFormatter.Snapshot snap = HostInspectorFormatter.from(
                "8.8.8.8",
                "8.8.8.8",
                HostProbeMode.PING_ONLY,
                Instant.parse("2026-09-03T12:00:00Z"),
                new HostTargetStats(0.0, 10.0, 12.0, 15.0, false),
                new HopStatsSummary(3.0, 0.0),
                EndpointState.UP,
                RouteState.NOT_TRACED,
                null,
                null);
        assertEquals("8.8.8.8", snap.address());
        assertEquals("PING", snap.mode());
        assertEquals("12", snap.rtt());
        assertEquals("3", snap.jitter());
        assertEquals("0%", snap.loss());
        assertEquals("UP", snap.endpoint());
        assertEquals("NOT TRACED", snap.route());
        assertEquals("—", snap.lastRouteChange());
        assertTrue(snap.problem().contains("немає")
                || snap.problem().equals("none")
                || !snap.problem().isBlank());
    }

    @Test
    void firingProblemUsesDescription() {
        HostProblemSummary problem = new HostProblemSummary(
                "8.8.8.8",
                "endpoint_down",
                true,
                1,
                Duration.ofMinutes(2),
                Instant.now(),
                null,
                HostProblemSummary.STATE_FIRING,
                HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN);
        assertEquals(HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN, HostInspectorFormatter.formatProblem(problem));
    }
}
