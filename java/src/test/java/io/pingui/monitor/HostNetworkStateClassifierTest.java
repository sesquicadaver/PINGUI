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
    void changedOnlyWhenPathComplete() {
        List<HopNode> hops = List.of(new HopNode(1, "10.0.0.1", 1.0, false), new HopNode(2, "8.8.8.8", 12.0, false));
        assertTrue(HostNetworkStateClassifier.targetReached(hops));
        assertEquals(RouteState.CHANGED, HostNetworkStateClassifier.route(HostProbeMode.TRACE, hops, true));
        assertEquals(RouteState.STABLE, HostNetworkStateClassifier.route(HostProbeMode.TRACE, hops, false));
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

    private static HostTargetStats upStats() {
        return new HostTargetStats(0.0, 10.0, 12.0, 15.0, false);
    }
}
