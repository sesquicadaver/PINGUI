package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutePollerTest {
    @Test
    void pollSuccess() {
        RouteSnapshot snapshot =
                new RouteSnapshot(
                        "8.8.8.8",
                        "8.8.8.8",
                        List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 10.0, false)));
        RoutePoller poller = new RoutePoller(new FakeRouteProbe(snapshot));
        HostPollOutcome outcome = poller.pollHostRoute("8.8.8.8", List.of(), 20, 0.5);
        assertFalse(outcome.routeChanged());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), outcome.currentIps());
    }

    @Test
    void pollDetectsChange() {
        RouteSnapshot snapshot =
                new RouteSnapshot(
                        "8.8.8.8",
                        "8.8.8.8",
                        List.of(new HopNode(1, "192.168.1.1", 2.0, false), new HopNode(2, "8.8.8.8", 4.0, false)));
        RoutePoller poller = new RoutePoller(new FakeRouteProbe(snapshot));
        HostPollOutcome outcome =
                poller.pollHostRoute("8.8.8.8", List.of("10.0.0.1", "8.8.8.8"), 20, 0.5);
        assertTrue(outcome.routeChanged());
    }
}
