package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.probe.ProbeOutcome;
import io.pingui.probe.RouteProbe;
import io.pingui.probe.TcpConnectProbe;
import java.net.ConnectException;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RoutePoller TCP connect path (P29-005). */
class RoutePollerTcpConnectTest {
    private static final RouteProbe UNUSED = (targetHost, maxHops, timeoutSeconds) -> {
        throw new UnsupportedOperationException();
    };

    @Test
    void successBuildsReachableSingleHopSnapshot() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        TcpConnectProbe probe =
                new TcpConnectProbe(hostname -> new InetAddress[] {loopback}, (address, port, timeoutMs) -> {});
        RoutePoller poller = new RoutePoller(UNUSED, null, probe);
        HostPollOutcome outcome = poller.pollHostTcpConnect("127.0.0.1:9", List.of(), 1.0);
        assertEquals(null, outcome.error());
        assertEquals(ProbeOutcome.SUCCESS, outcome.probeOutcome());
        assertTrue(outcome.snapshot().nodes().get(0).isReachable());
        assertEquals("127.0.0.1", outcome.snapshot().targetIp());
    }

    @Test
    void refusedYieldsUnreachableSnapshotNotProbeError() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        TcpConnectProbe probe =
                new TcpConnectProbe(hostname -> new InetAddress[] {loopback}, (address, port, timeoutMs) -> {
                    throw new ConnectException("refused");
                });
        RoutePoller poller = new RoutePoller(UNUSED, null, probe);
        HostPollOutcome outcome = poller.pollHostTcpConnect("127.0.0.1:1", List.of(), 0.5);
        assertEquals(null, outcome.error());
        assertEquals(ProbeOutcome.REFUSED, outcome.probeOutcome());
        assertFalse(outcome.snapshot().nodes().get(0).isReachable());
        assertEquals(Models.TIMEOUT_IP, outcome.snapshot().nodes().get(0).ip());
    }

    @Test
    void dnsErrorBecomesProbeError() {
        TcpConnectProbe probe = new TcpConnectProbe(
                hostname -> {
                    throw new java.net.UnknownHostException("nxdomain");
                },
                (address, port, timeoutMs) -> {
                    throw new AssertionError();
                });
        RoutePoller poller = new RoutePoller(UNUSED, null, probe);
        HostPollOutcome outcome = poller.pollHostTcpConnect("no.such:443", List.of("1.1.1.1"), 1.0);
        assertTrue(outcome.error().contains("DNS"));
        assertEquals(ProbeOutcome.DNS_ERROR, outcome.probeOutcome());
        assertEquals(List.of("1.1.1.1"), outcome.currentIps());
    }
}
