package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteIdentityTest {
    @Test
    void fromHopsPreservesTimeoutSlots() {
        RouteIdentity id = RouteIdentity.fromHops(List.of(
                new HopNode(1, "10.0.0.1", 1.0, false), Models.timeout(2), new HopNode(3, "8.8.8.8", 9.0, false)));
        assertEquals(List.of("10.0.0.1", "*", "8.8.8.8"), id.tokens());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), id.reachableIps());
    }

    @Test
    void sameTopologyIgnoresTransientTimeout() {
        RouteIdentity stable = RouteIdentity.fromHops(List.of(
                new HopNode(1, "10.0.0.1", 1.0, false),
                new HopNode(2, "10.0.0.2", 2.0, false),
                new HopNode(3, "8.8.8.8", 3.0, false)));
        RouteIdentity lossy = RouteIdentity.fromHops(List.of(
                new HopNode(1, "10.0.0.1", 1.0, false), Models.timeout(2), new HopNode(3, "8.8.8.8", 3.0, false)));
        assertTrue(stable.sameTopology(lossy));
        assertTrue(lossy.sameTopology(stable));
    }

    @Test
    void sameTopologyDetectsIpRewrite() {
        RouteIdentity a = RouteIdentity.fromReachableIps(List.of("10.0.0.1", "8.8.8.8"));
        RouteIdentity b = RouteIdentity.fromReachableIps(List.of("9.9.9.9", "8.8.8.8"));
        assertFalse(a.sameTopology(b));
    }

    @Test
    void mergeKnownFillsTimeouts() {
        RouteIdentity known = RouteIdentity.fromReachableIps(List.of("10.0.0.1", "10.0.0.2", "8.8.8.8"));
        RouteIdentity lossy = RouteIdentity.fromHops(List.of(
                new HopNode(1, "10.0.0.1", 1.0, false), Models.timeout(2), new HopNode(3, "8.8.8.8", 3.0, false)));
        assertEquals(
                List.of("10.0.0.1", "10.0.0.2", "8.8.8.8"),
                known.mergeKnown(lossy).tokens());
        assertEquals(
                List.of("10.0.0.1", "10.0.0.2", "8.8.8.8"),
                lossy.mergeKnown(known).tokens());
    }
}
