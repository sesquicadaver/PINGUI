package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.probe.icmp.ProbeResult;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
    void discoveryDoesNotSampleTargetUntilLastHop() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("10.0.0.2", 6.0, false),
                new ProbeResult("8.8.8.8", 8.0, true));

        MtrPollOutcome hop1 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertFalse(hop1.targetSampled());
        assertEquals(1, hop1.probedHop());
        assertEquals("10.0.0.1", hop1.freshHopSample().ip());

        MtrPollOutcome hop2 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertFalse(hop2.targetSampled());
        assertEquals(2, hop2.probedHop());

        MtrPollOutcome hop3 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertTrue(hop3.targetSampled());
        assertEquals(MtrTargetOutcome.REACHABLE, hop3.targetOutcome());
        assertEquals(List.of("10.0.0.1", "10.0.0.2", "8.8.8.8"), hop3.lastCompleteRouteIps());
    }

    @Test
    void monitoringEmitsOnlyFreshHopSample() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("8.8.8.8", 8.0, true),
                new ProbeResult("10.0.0.1", 5.0, false));

        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        MtrPollOutcome refresh = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(1, refresh.probedHop());
        assertEquals(5.0, refresh.freshHopSample().pingMs());
        assertFalse(refresh.targetSampled());
        assertEquals(2, refresh.completeRoute().nodes().size());
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
        assertNull(mtrProbe.stateFor("8.8.8.8"));
    }

    @Test
    void renameHostClearsOldAndNewKeys() {
        prober.enqueue(new ProbeResult("10.0.0.1", 4.0, false));
        mtrProbe.poll("old.example", 20, 0.5);
        assertNotNull(mtrProbe.stateFor("old.example"));

        mtrProbe.renameHost("old.example", "new.example");
        assertNull(mtrProbe.stateFor("old.example"));
        assertNull(mtrProbe.stateFor("new.example"));
    }

    @Test
    void resetDuringInFlightPollDoesNotResurrectState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BlockingMtrHopProber blocking = new BlockingMtrHopProber("8.8.8.8", entered, release);
        MtrProbe concurrent = new MtrProbe(blocking);

        AtomicReference<MtrPollOutcome> outcome = new AtomicReference<>();
        Thread poller = new Thread(() -> outcome.set(concurrent.poll("8.8.8.8", 20, 0.5)), "mtr-poll");
        poller.start();
        assertTrue(entered.await(3, TimeUnit.SECONDS));

        concurrent.resetHost("8.8.8.8");
        assertNull(concurrent.stateFor("8.8.8.8"));

        release.countDown();
        poller.join(3_000);
        assertFalse(poller.isAlive());
        assertNotNull(outcome.get());
        assertNull(concurrent.stateFor("8.8.8.8"));
    }

    @Test
    void resolvesTargetIpOnFirstPoll() {
        prober.enqueue(new ProbeResult("10.0.0.1", 4.0, false));
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals("8.8.8.8", mtrProbe.stateFor("8.8.8.8").targetIp());
    }

    @Test
    void intermediateTimeoutDoesNotShrinkMonitoringSpan() {
        // discover 3 hops, then hop1 OK, hop2 timeout → must still probe hop3 (target)
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("10.0.0.2", 6.0, false),
                new ProbeResult("8.8.8.8", 8.0, true),
                new ProbeResult("10.0.0.1", 4.5, false));
        prober.enqueueTimeout();
        prober.enqueue(new ProbeResult("8.8.8.8", 9.0, true));

        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(3, mtrProbe.stateFor("8.8.8.8").targetHop());
        assertEquals(3, mtrProbe.stateFor("8.8.8.8").monitoringSpan());

        mtrProbe.poll("8.8.8.8", 20, 0.5); // hop1
        MtrPollOutcome midTimeout = mtrProbe.poll("8.8.8.8", 20, 0.5); // hop2 timeout
        assertEquals(2, midTimeout.probedHop());
        assertFalse(midTimeout.targetSampled());
        assertEquals(MtrTargetOutcome.NOT_SAMPLED, midTimeout.targetOutcome());
        assertEquals(3, mtrProbe.stateFor("8.8.8.8").monitoringSpan());
        assertEquals(3, mtrProbe.stateFor("8.8.8.8").cursor());

        MtrPollOutcome targetStillSampled = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(3, targetStillSampled.probedHop());
        assertTrue(targetStillSampled.targetSampled());
        assertEquals(MtrTargetOutcome.REACHABLE, targetStillSampled.targetOutcome());
        assertEquals(1, mtrProbe.stateFor("8.8.8.8").cursor());
    }

    @Test
    void intermediateTimeoutThenRecoveryKeepsFullRotation() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("10.0.0.2", 6.0, false),
                new ProbeResult("8.8.8.8", 8.0, true),
                new ProbeResult("10.0.0.1", 4.1, false));
        prober.enqueueTimeout();
        prober.enqueue(
                new ProbeResult("8.8.8.8", 8.5, true),
                new ProbeResult("10.0.0.1", 4.2, false),
                new ProbeResult("10.0.0.2", 6.5, false),
                new ProbeResult("8.8.8.8", 9.0, true));

        for (int i = 0; i < 3; i++) {
            mtrProbe.poll("8.8.8.8", 20, 0.5);
        }
        mtrProbe.poll("8.8.8.8", 20, 0.5); // hop1
        mtrProbe.poll("8.8.8.8", 20, 0.5); // hop2 timeout
        mtrProbe.poll("8.8.8.8", 20, 0.5); // hop3 target
        mtrProbe.poll("8.8.8.8", 20, 0.5); // hop1 again
        MtrPollOutcome hop2Recovered = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(2, hop2Recovered.probedHop());
        assertEquals("10.0.0.2", hop2Recovered.freshHopSample().ip());
        assertTrue(hop2Recovered.freshHopSample().isReachable());
        assertEquals(3, mtrProbe.stateFor("8.8.8.8").cursor());

        MtrPollOutcome hop3 = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(3, hop3.probedHop());
        assertTrue(hop3.targetSampled());
    }

    @Test
    void repeatedTargetTimeoutsRemainIdentifiable() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("8.8.8.8", 8.0, true),
                new ProbeResult("10.0.0.1", 4.0, false));
        prober.enqueueTimeout();
        prober.enqueue(new ProbeResult("10.0.0.1", 4.1, false));
        prober.enqueueTimeout();

        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5); // hop1
        MtrPollOutcome firstTargetTimeout = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(2, firstTargetTimeout.probedHop());
        assertTrue(firstTargetTimeout.targetSampled());
        assertEquals(MtrTargetOutcome.UNREACHABLE, firstTargetTimeout.targetOutcome());
        assertEquals(2, mtrProbe.stateFor("8.8.8.8").targetHop());
        assertEquals(1, mtrProbe.stateFor("8.8.8.8").cursor());

        mtrProbe.poll("8.8.8.8", 20, 0.5); // hop1
        MtrPollOutcome secondTargetTimeout = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(2, secondTargetTimeout.probedHop());
        assertTrue(secondTargetTimeout.targetSampled());
        assertEquals(MtrTargetOutcome.UNREACHABLE, secondTargetTimeout.targetOutcome());
    }

    @Test
    void targetRecoversAfterTimeout() {
        prober.enqueue(
                new ProbeResult("10.0.0.1", 4.0, false),
                new ProbeResult("8.8.8.8", 8.0, true),
                new ProbeResult("10.0.0.1", 4.0, false));
        prober.enqueueTimeout();
        prober.enqueue(new ProbeResult("10.0.0.1", 4.2, false), new ProbeResult("8.8.8.8", 9.5, true));

        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        mtrProbe.poll("8.8.8.8", 20, 0.5);
        MtrPollOutcome down = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(MtrTargetOutcome.UNREACHABLE, down.targetOutcome());

        mtrProbe.poll("8.8.8.8", 20, 0.5);
        MtrPollOutcome up = mtrProbe.poll("8.8.8.8", 20, 0.5);
        assertEquals(2, up.probedHop());
        assertTrue(up.targetSampled());
        assertEquals(MtrTargetOutcome.REACHABLE, up.targetOutcome());
        assertEquals(9.5, up.freshHopSample().pingMs());
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

        void enqueueTimeout() {
            script.addLast(Optional.empty());
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

    private static final class BlockingMtrHopProber implements MtrHopProber {
        private final String targetIp;
        private final CountDownLatch entered;
        private final CountDownLatch release;

        BlockingMtrHopProber(String targetIp, CountDownLatch entered, CountDownLatch release) {
            this.targetIp = targetIp;
            this.entered = entered;
            this.release = release;
        }

        @Override
        public String resolveTargetIp(String targetHost) {
            return targetIp;
        }

        @Override
        public Optional<ProbeResult> probeHop(String targetHost, String targetIp, int hop, double timeoutSeconds) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("probe not released");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("probe interrupted", ex);
            }
            return Optional.of(new ProbeResult("10.0.0.1", 4.0, false));
        }
    }
}
