package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteChangeDetectorTest {
    @Test
    void noChangeOnFirstObservation() {
        var result = RouteChangeDetector.detect(List.of(), List.of("10.0.0.1"));
        assertFalse(result.changed());
    }

    @Test
    void detectsChange() {
        var result = RouteChangeDetector.detect(List.of("10.0.0.1"), List.of("192.168.1.1"));
        assertTrue(result.changed());
    }

    @Test
    void noChangeWhenEqual() {
        var result = RouteChangeDetector.detect(List.of("10.0.0.1", "8.8.8.8"), List.of("10.0.0.1", "8.8.8.8"));
        assertFalse(result.changed());
    }

    @Test
    void transientTimeoutIsNotRouteChange() {
        CandidateRouteFsm fsm = new CandidateRouteFsm();
        RouteSnapshot stable = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(
                        new HopNode(1, "10.0.0.1", 4.0, false),
                        new HopNode(2, "10.0.0.2", 6.0, false),
                        new HopNode(3, "8.8.8.8", 8.0, false)));
        RouteChangeDetector.observe(fsm, stable, true, List.of());
        RouteSnapshot midTimeout = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(
                        new HopNode(1, "10.0.0.1", 4.0, false),
                        Models.timeout(2),
                        new HopNode(3, "8.8.8.8", 8.0, false)));
        var result = RouteChangeDetector.observe(fsm, midTimeout, true, List.of("10.0.0.1", "10.0.0.2", "8.8.8.8"));
        assertFalse(result.changed());
    }

    @Test
    void confirmedTargetChangeEmitsOnce() {
        CandidateRouteFsm fsm = new CandidateRouteFsm();
        RouteSnapshot first = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 4.0, false), new HopNode(2, "8.8.8.8", 8.0, false)));
        assertFalse(RouteChangeDetector.observe(fsm, first, true, List.of()).changed());

        RouteSnapshot candidatePartial =
                new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "9.9.9.9", 4.0, false)));
        assertFalse(RouteChangeDetector.observe(fsm, candidatePartial, false, List.of())
                .changed());

        RouteSnapshot confirmed = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "9.9.9.9", 4.0, false), new HopNode(2, "8.8.8.8", 8.0, false)));
        var result = RouteChangeDetector.observe(fsm, confirmed, true, List.of());
        assertTrue(result.changed());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), result.oldIps());
        assertEquals(List.of("9.9.9.9", "8.8.8.8"), result.newIps());
    }

    @Test
    void targetReachedDetectsTargetIp() {
        RouteSnapshot snapshot = new RouteSnapshot(
                "dns.google",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 1.0, false), new HopNode(2, "8.8.8.8", 2.0, false)));
        assertTrue(RouteChangeDetector.targetReached(snapshot));
    }
}
