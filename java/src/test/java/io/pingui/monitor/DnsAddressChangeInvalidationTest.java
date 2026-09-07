package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.dns.DnsControlEvent;
import io.pingui.dns.DnsLookupOutcome;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.MtrHopProber;
import io.pingui.probe.MtrProbe;
import io.pingui.probe.icmp.ProbeResult;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** P35-006: confirmed DNS address-set change invalidates MTR target / candidate / latency. */
class DnsAddressChangeInvalidationTest {
    private static final Instant T0 = Instant.parse("2026-09-07T18:00:00Z");

    @Test
    void invalidateClearsMtrTargetIpAndCandidateRoute() {
        ScriptMtrHopProber prober = new ScriptMtrHopProber("1.2.3.4");
        MtrProbe mtr = new MtrProbe(prober);
        prober.enqueue(new ProbeResult("1.2.3.4", 5.0, true));
        mtr.poll("dns.example", 20, 0.5);
        assertEquals(Optional.of("1.2.3.4"), mtr.currentTargetIp("dns.example"));

        RoutePoller poller =
                new RoutePoller((host, maxHops, timeout) -> new RouteSnapshot(host, "1.2.3.4", List.of()), mtr);
        CandidateRouteFsm fsm = poller.fsmFor("dns.example");
        fsm.seedActiveIfEmpty(RouteIdentity.fromReachableIps(List.of("10.0.0.1", "1.2.3.4")));
        assertFalse(fsm.active().isEmpty());

        poller.invalidateOnDnsAddressChange("dns.example");

        assertTrue(mtr.currentTargetIp("dns.example").isEmpty());
        assertTrue(fsm.active().isEmpty());
        assertTrue(fsm.candidate().isEmpty());
    }

    @Test
    void applyDnsControlEventChangeInvalidatesButOkDoesNot() {
        ScriptMtrHopProber prober = new ScriptMtrHopProber("9.9.9.9");
        MtrProbe mtr = new MtrProbe(prober);
        prober.enqueue(new ProbeResult("9.9.9.9", 4.0, true));
        mtr.poll("host.example", 20, 0.5);
        assertEquals(Optional.of("9.9.9.9"), mtr.currentTargetIp("host.example"));

        MonitorService service =
                new MonitorService(2.0, 20, 0.5, (h, m, t) -> new RouteSnapshot(h, "9.9.9.9", List.of()), mtr);

        service.applyDnsControlEvent(new DnsControlEvent(
                "host.example", "ok", "resolved", List.of(), List.of("9.9.9.9"), 1L, DnsLookupOutcome.OK, T0));
        assertEquals(Optional.of("9.9.9.9"), mtr.currentTargetIp("host.example"));

        service.applyDnsControlEvent(new DnsControlEvent(
                "host.example",
                "change",
                "DNS address set changed",
                List.of("9.9.9.9"),
                List.of("8.8.8.8"),
                2L,
                DnsLookupOutcome.OK,
                T0.plusSeconds(1)));
        assertTrue(mtr.currentTargetIp("host.example").isEmpty());
        service.close();
    }

    @Test
    void pollPathDnsChangeInvalidatesMtrTarget() throws Exception {
        ScriptMtrHopProber prober = new ScriptMtrHopProber("1.1.1.1");
        MtrProbe mtr = new MtrProbe(prober);
        prober.enqueue(new ProbeResult("1.1.1.1", 3.0, true));
        mtr.poll("failover.example", 20, 0.5);
        assertEquals(Optional.of("1.1.1.1"), mtr.currentTargetIp("failover.example"));

        AtomicReference<InetAddress[]> addrs =
                new AtomicReference<>(new InetAddress[] {InetAddress.getByName("1.1.1.1")});
        CountDownLatch baseline = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);
        MonitorService service = new MonitorService(
                0.05,
                20,
                0.5,
                (h, m, t) -> new RouteSnapshot(h, "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 3.0, false))),
                mtr);
        service.setHostProbeModeResolver(host -> HostProbeMode.TRACE);
        service.setForwardDnsLookupForTests(hostname -> {
            InetAddress[] current = addrs.get();
            String ip = current[0].getHostAddress();
            if ("1.1.1.1".equals(ip)) {
                baseline.countDown();
            } else if ("8.8.8.8".equals(ip)) {
                changed.countDown();
            }
            return current;
        });
        service.setListener(new MonitorService.Listener() {
            @Override
            public void onDataReceived(String host, RouteSnapshot snapshot) {}

            @Override
            public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {}

            @Override
            public void onProbeError(String host, String message) {}
        });
        service.addHost("failover.example", true, HostProbeMode.TRACE);

        assertTrue(baseline.await(5, TimeUnit.SECONDS), "expected DNS baseline observe");
        addrs.set(new InetAddress[] {InetAddress.getByName("8.8.8.8")});
        assertTrue(changed.await(5, TimeUnit.SECONDS), "expected DNS change observe");
        assertTrue(
                waitUntil(() -> mtr.currentTargetIp("failover.example").isEmpty(), 5_000),
                "MTR targetIp must clear after DNS address-set change");
        service.close();
    }

    private static boolean waitUntil(java.util.concurrent.Callable<Boolean> condition, long timeoutMs)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return true;
            }
            Thread.sleep(25);
        }
        return Boolean.TRUE.equals(condition.call());
    }

    private static final class ScriptMtrHopProber implements MtrHopProber {
        private final String resolvedIp;
        private final Deque<ProbeResult> queue = new ArrayDeque<>();

        ScriptMtrHopProber(String resolvedIp) {
            this.resolvedIp = resolvedIp;
        }

        void enqueue(ProbeResult... results) {
            for (ProbeResult result : results) {
                queue.addLast(result);
            }
        }

        @Override
        public Optional<ProbeResult> probeHop(String targetHost, String targetIp, int hop, double timeoutSeconds) {
            ProbeResult next = queue.pollFirst();
            return next == null ? Optional.empty() : Optional.of(next);
        }

        @Override
        public String resolveTargetIp(String targetHost) {
            return resolvedIp;
        }
    }
}
