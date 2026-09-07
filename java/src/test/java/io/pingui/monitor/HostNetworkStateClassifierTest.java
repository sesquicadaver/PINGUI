package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostNetworkStateClassifierTest {
    @Test
    void pingOnlyIsNotTracedEvenWithHops() {
        List<HopNode> hops = List.of(new HopNode(1, "8.8.8.8", 12.0, false));
        assertEquals(RouteState.NOT_TRACED, HostNetworkStateClassifier.route(HostProbeMode.PING_ONLY, hops, false));
        assertEquals(RouteState.NOT_TRACED, HostNetworkStateClassifier.route(HostProbeMode.TCP_CONNECT, hops, true));
    }

    @Test
    void emptyTraceIsNotTracedNotError() {
        assertEquals(RouteState.NOT_TRACED, HostNetworkStateClassifier.route(HostProbeMode.TRACE, List.of(), false));
        assertEquals(RouteState.NOT_TRACED, HostNetworkStateClassifier.route(HostProbeMode.MTR, null, false));
    }

    @Test
    void incompleteWhenLastHopTimedOut() {
        List<HopNode> hops = List.of(new HopNode(1, "10.0.0.1", 1.0, false), Models.timeout(2));
        assertEquals(RouteState.INCOMPLETE, HostNetworkStateClassifier.route(HostProbeMode.TRACE, hops, true));
        assertFalse(HostNetworkStateClassifier.targetReached(hops));
    }

    @Test
    void incompleteWhenLastHopIsRouterNotTarget() {
        List<HopNode> hops = List.of(new HopNode(1, "10.0.0.1", 1.0, false));
        assertFalse(HostNetworkStateClassifier.targetReached(hops, "8.8.8.8"));
        assertEquals(
                RouteState.INCOMPLETE, HostNetworkStateClassifier.route(HostProbeMode.MTR, hops, false, "8.8.8.8"));
    }

    @Test
    void changedOnlyWhenPathComplete() {
        List<HopNode> hops = List.of(new HopNode(1, "10.0.0.1", 1.0, false), new HopNode(2, "8.8.8.8", 12.0, false));
        assertTrue(HostNetworkStateClassifier.targetReached(hops));
        assertTrue(HostNetworkStateClassifier.targetReached(hops, "8.8.8.8"));
        assertEquals(RouteState.CHANGED, HostNetworkStateClassifier.route(HostProbeMode.TRACE, hops, true));
        assertEquals(RouteState.STABLE, HostNetworkStateClassifier.route(HostProbeMode.TRACE, hops, false));
        assertEquals(RouteState.STABLE, HostNetworkStateClassifier.route(HostProbeMode.MTR, hops, false, "8.8.8.8"));
    }

    @Test
    void endpointIndependentOfRoute() {
        assertEquals(EndpointState.UNKNOWN, HostNetworkStateClassifier.endpoint(false, upStats()));
        assertEquals(EndpointState.UNKNOWN, HostNetworkStateClassifier.endpoint(true, null));
        assertEquals(EndpointState.UP, HostNetworkStateClassifier.endpoint(true, upStats()));
        assertEquals(
                EndpointState.DEGRADED,
                HostNetworkStateClassifier.endpoint(true, new HostTargetStats(15.0, 10.0, 12.0, 40.0, false)));
        assertEquals(
                EndpointState.DOWN,
                HostNetworkStateClassifier.endpoint(true, new HostTargetStats(100.0, null, null, null, true)));
    }

    @Test
    void currentTimeoutIsDownEvenWithHealthyHistory() {
        // After successful probes, avg/loss look fine but the latest target sample timed out.
        HostTargetStats timeoutAfterSuccess = new HostTargetStats(1.0, 10.0, 12.0, 15.0, true);
        assertEquals(EndpointState.DOWN, HostNetworkStateClassifier.endpoint(true, timeoutAfterSuccess));
        assertEquals(
                EndpointState.DOWN,
                HostNetworkStateClassifier.endpoint(true, new HostTargetStats(0.0, 8.0, 9.0, 11.0, true)));
    }

    @Test
    void highSessionLossWithoutTimeoutIsStillDownOrDegraded() {
        assertEquals(
                EndpointState.DOWN,
                HostNetworkStateClassifier.endpoint(true, new HostTargetStats(60.0, 10.0, 12.0, 20.0, false)));
        assertEquals(
                EndpointState.DEGRADED,
                HostNetworkStateClassifier.endpoint(true, new HostTargetStats(25.0, 10.0, 12.0, 20.0, false)));
    }

    private static HostTargetStats upStats() {
        return new HostTargetStats(0.0, 10.0, 12.0, 15.0, false);
    }
}
