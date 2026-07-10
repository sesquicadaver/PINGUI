package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.MtrProbe;
import io.pingui.probe.icmp.ProbeResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoutePollerTest {
    @Test
    void pollSuccess() {
        RouteSnapshot snapshot = new RouteSnapshot(
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
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "192.168.1.1", 2.0, false), new HopNode(2, "8.8.8.8", 4.0, false)));
        RoutePoller poller = new RoutePoller(new FakeRouteProbe(snapshot));
        HostPollOutcome outcome = poller.pollHostRoute("8.8.8.8", List.of("10.0.0.1", "8.8.8.8"), 20, 0.5);
        assertTrue(outcome.routeChanged());
    }

    @Test
    void pollHandlesIoFailure() {
        RoutePoller poller = new RoutePoller(FailingRouteProbe.io("network down"));
        HostPollOutcome outcome = poller.pollHostRoute("8.8.8.8", List.of("10.0.0.1"), 20, 0.5);
        assertEquals("network down", outcome.error());
        assertEquals(List.of("10.0.0.1"), outcome.currentIps());
    }

    @Test
    void pollHandlesRuntimeFailure() {
        RoutePoller poller = new RoutePoller(FailingRouteProbe.runtime("bad state"));
        HostPollOutcome outcome = poller.pollHostRoute("8.8.8.8", List.of(), 20, 0.5);
        assertEquals("bad state", outcome.error());
    }

    @Test
    void pollHostMtrDetectsIncrementalRouteChange() {
        ScriptMtrHopProber prober = new ScriptMtrHopProber();
        prober.enqueue(new ProbeResult("10.0.0.1", 4.0, false), new ProbeResult("8.8.8.8", 8.0, true));
        RoutePoller poller = new RoutePoller(
                new FakeRouteProbe(new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of())), new MtrProbe(prober));

        HostPollOutcome first = poller.pollHostMtr("8.8.8.8", List.of(), 20, 0.5);
        assertFalse(first.routeChanged());
        assertEquals(List.of("10.0.0.1"), first.currentIps());

        HostPollOutcome second = poller.pollHostMtr("8.8.8.8", List.of("10.0.0.1"), 20, 0.5);
        assertTrue(second.routeChanged());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), second.currentIps());
    }

    private static final class ScriptMtrHopProber implements io.pingui.probe.MtrHopProber {
        private final java.util.ArrayDeque<Optional<ProbeResult>> script = new java.util.ArrayDeque<>();

        void enqueue(ProbeResult... results) {
            for (ProbeResult result : results) {
                script.addLast(Optional.of(result));
            }
        }

        @Override
        public String resolveTargetIp(String targetHost) {
            return targetHost;
        }

        @Override
        public Optional<ProbeResult> probeHop(String targetHost, String targetIp, int hop, double timeoutSeconds) {
            return script.removeFirst();
        }
    }
}
