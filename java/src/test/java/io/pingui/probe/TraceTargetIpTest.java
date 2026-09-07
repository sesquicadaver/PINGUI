package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.RouteChangeDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceTargetIpTest {

    @Test
    void literalTargetIgnoresHopList() {
        List<String> lines = List.of("traceroute to 8.8.8.8 (8.8.8.8), 30 hops max", " 1  10.0.0.1  1 ms");
        assertEquals("8.8.8.8", TraceTargetIp.resolve("8.8.8.8", lines));
    }

    @Test
    void unixHeaderProvidesTargetWhenHostIsName() {
        List<String> lines = List.of(
                "traceroute to dns.google (8.8.8.8), 30 hops max", " 1  10.0.0.1  1.0 ms", " 2  10.0.0.2  2.0 ms");
        assertEquals("8.8.8.8", TraceTargetIp.resolve("dns.google", lines));
    }

    @Test
    void windowsBracketHeaderProvidesTarget() {
        List<String> lines =
                List.of("Tracing route to dns.google [8.8.8.8]", "  1     1 ms     1 ms     1 ms  192.168.1.1");
        assertEquals("8.8.8.8", TraceTargetIp.resolve("dns.google", lines));
    }

    @Test
    void windowsLiteralHeaderProvidesTarget() {
        List<String> lines = List.of(
                "Tracing route to 8.8.8.8 over a maximum of 30 hops", "  1     1 ms     1 ms     1 ms  192.168.1.1");
        assertEquals("8.8.8.8", TraceTargetIp.resolve("8.8.8.8", lines));
    }

    @Test
    void incompleteTraceSnapshotDoesNotUseLastHopAsTarget() {
        List<String> lines = List.of(
                "traceroute to dns.google (8.8.8.8), 30 hops max",
                " 1  10.0.0.1  1.0 ms",
                " 2  10.0.0.2  2.0 ms",
                " 3  * * *");
        List<HopNode> nodes = ProcessRouteProbe.parseUnix(lines);
        RouteSnapshot snapshot = ProcessRouteProbe.toSnapshot("dns.google", lines, nodes);
        assertEquals("8.8.8.8", snapshot.targetIp());
        assertFalse(RouteChangeDetector.targetReached(snapshot));
        assertFalse(snapshot.routeIps().contains("8.8.8.8"));
    }

    @Test
    void completeTraceConfirmsTargetFromHeader() {
        List<String> lines = List.of(
                "traceroute to dns.google (8.8.8.8), 30 hops max", " 1  10.0.0.1  1.0 ms", " 2  8.8.8.8  10.0 ms");
        List<HopNode> nodes = ProcessRouteProbe.parseUnix(lines);
        RouteSnapshot snapshot = ProcessRouteProbe.toSnapshot("dns.google", lines, nodes);
        assertEquals("8.8.8.8", snapshot.targetIp());
        assertTrue(RouteChangeDetector.targetReached(snapshot));
    }

    @Test
    void blankHostWithoutHeaderYieldsNullTargetIp() {
        assertNull(TraceTargetIp.resolve(" ", List.of(" 1  10.0.0.1  1 ms")));
    }
}
