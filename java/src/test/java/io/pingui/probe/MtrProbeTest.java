package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.pingui.probe.icmp.ProbeResult;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MtrProbeTest {
    private ScriptMtrHopProber prober;
    private MtrProbe mtrProbe;

    @BeforeEach
    void setUp() {
        prober = new ScriptMtrHopProber("8.8.8.8");
        mtrProbe = new MtrProbe(prober);
    }

    @Test
    void discoversRouteHopByHop() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("10.0.0.2", 6.0, false),
                new ProbeResult("8.8.8.8", 8.0, true));

        MtrPollOutcome hop1 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertNotNull(hop1.snapshot());
        assertEquals(List.of("10.0.0.1"), hop1.snapshot().routeIps());
        assertEquals(
                MtrProbeState.Phase.DISCOVERING, mtrProbe.stateFor("8.8.8.8").phase());

        MtrPollOutcome hop2 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), hop2.snapshot().routeIps());

        MtrPollOutcome hop3 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(List.of("10.0.0.1", "10.0.0.2", "8.8.8.8"), hop3.snapshot().routeIps());
        assertEquals(
                MtrProbeState.Phase.MONITORING, mtrProbe.stateFor("8.8.8.8").phase());
        assertEquals(1, mtrProbe.stateFor("8.8.8.8").cursor());
    }

    @Test
    void monitoringRotatesCursor() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("8.8.8.8", 8.0, true),
                new ProbeResult("10.0.0.1", 5.0, false),
                new ProbeResult("8.8.8.8", 9.0, true));

        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(
                MtrProbeState.Phase.MONITORING, mtrProbe.stateFor("8.8.8.8").phase());

        MtrPollOutcome refreshHop1 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(5.0, refreshHop1.snapshot().nodes().get(0).pingMs());
        assertEquals(2, mtrProbe.stateFor("8.8.8.8").cursor());

        MtrPollOutcome refreshHop2 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(9.0, refreshHop2.snapshot().nodes().get(1).pingMs());
        assertEquals(1, mtrProbe.stateFor("8.8.8.8").cursor());
    }

    @Test
    void detectsRouteChangeDuringMonitoring() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("8.8.8.8", 8.0, true),
                new ProbeResult("10.0.0.1", 4.5, false),
                new ProbeResult("192.168.1.1", 3.0, false));

        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        MtrPollOutcome changed = mtrProbe.poll("8.8.8.8", 20, 0.5);

        assertEquals(
                MtrProbeState.Phase.DISCOVERING, mtrProbe.stateFor("8.8.8.8").phase());
        assertEquals(3, mtrProbe.stateFor("8.8.8.8").cursor());
        assertEquals(List.of("10.0.0.1", "192.168.1.1"), changed.snapshot().routeIps());
    }

    @Test
    void timeoutDuringDiscoveryAdvancesCursor() {
        prober.enqueueTimeout(new ProbeResult("10.0.0.1", 4.0, false));

        MtrPollOutcome first = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(1, first.snapshot().nodes().size());
        assertFalse(first.snapshot().nodes().get(0).isReachable());

        MtrPollOutcome second = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(List.of("10.0.0.1"), second.snapshot().routeIps());
        assertEquals(3, mtrProbe.stateFor("8.8.8.8").cursor());
    }

    @Test
    void resetHostClearsState() {
        prober.enqueue(new ProbeResult("10.0.0.1", 4.0, false));
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertNotNull(mtrProbe.stateFor("8.8.8.8"));

        mtrProbe.resetHost("8.8.8.8");
        assertEquals(null, mtrProbe.stateFor("8.8.8.8"));
    }

    @Test
    void resolvesTargetIpOnFirstPoll() {
        prober.enqueue(new ProbeResult("10.0.0.1", 4.0, false));
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals("8.8.8.8", mtrProbe.stateFor("8.8.8.8").targetIp());
    }

    private static final class ScriptMtrHopProber implements MtrHopProber {
        private final String targetIp;
        private final Deque<Optional<ProbeResult>> script = new ArrayDeque<>();

        ScriptMtrHopProber(String targetIp) {
            this.targetIp = targetIp;
        }

        void enqueue(ProbeResult... results) {
            for (ProbeResult result : results) {
                script.addLast(Optional.of(result));
            }
        }

        void enqueueTimeout(ProbeResult... results) {
            script.addLast(Optional.empty());
            enqueue(results);
        }

        @Override
        public String resolveTargetIp(String targetHost) {
            return targetIp;
        }

        @Override
        public Optional<ProbeResult> probeHop(String targetHost, String targetIp, int hop, double timeoutSeconds) {
            if (script.isEmpty()) {
                throw new IllegalStateException("No scripted probe for hop " + hop);
            }
            return script.removeFirst();
        }
    }
}
