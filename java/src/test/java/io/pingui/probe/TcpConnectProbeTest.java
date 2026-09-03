package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TcpConnectProbe} (P29-005). */
class TcpConnectProbeTest {
    @Test
    void successReportsResolvedIpAndConnectTime() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        TcpConnectProbe probe =
                new TcpConnectProbe(hostname -> new InetAddress[] {loopback}, (address, port, timeoutMs) -> {
                    /* success */
                });
        TcpConnectResult result = probe.probe("127.0.0.1:9", 1.0);
        assertEquals(TcpConnectOutcome.SUCCESS, result.outcome());
        assertEquals("127.0.0.1", result.resolvedIp());
        assertTrue(result.message().contains("connected"));
        assertTrue(result.message().contains("dns"));
    }

    @Test
    void refusedAndTimeoutAreDistinct() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        TcpConnectProbe refused =
                new TcpConnectProbe(hostname -> new InetAddress[] {loopback}, (address, port, timeoutMs) -> {
                    throw new ConnectException("Connection refused");
                });
        assertEquals(
                TcpConnectOutcome.REFUSED, refused.probe("127.0.0.1:1", 0.5).outcome());

        TcpConnectProbe timedOut =
                new TcpConnectProbe(hostname -> new InetAddress[] {loopback}, (address, port, timeoutMs) -> {
                    throw new SocketTimeoutException("timed out");
                });
        assertEquals(
                TcpConnectOutcome.TIMEOUT, timedOut.probe("127.0.0.1:1", 0.5).outcome());
    }

    @Test
    void dnsFailureDoesNotDial() {
        TcpConnectProbe probe = new TcpConnectProbe(
                hostname -> {
                    throw new UnknownHostException("nxdomain");
                },
                (address, port, timeoutMs) -> {
                    throw new AssertionError("must not dial");
                });
        TcpConnectResult result = probe.probe("missing.example:443", 1.0);
        assertEquals(TcpConnectOutcome.DNS_ERROR, result.outcome());
        assertTrue(result.resolvedIp().isEmpty());
    }

    @Test
    void hostnameLookupPrefersIpv4AndMapsGenericIoError() throws Exception {
        InetAddress v6 = InetAddress.getByName("2001:db8::1");
        InetAddress v4 = InetAddress.getByName("9.9.9.9");
        TcpConnectProbe preferV4 =
                new TcpConnectProbe(hostname -> new InetAddress[] {v6, v4}, (address, port, timeoutMs) -> {
                    assertEquals("9.9.9.9", address.getHostAddress());
                });
        TcpConnectResult ok = preferV4.probe("dns.example:443", 1.0);
        assertEquals(TcpConnectOutcome.SUCCESS, ok.outcome());
        assertEquals("9.9.9.9", ok.resolvedIp());

        TcpConnectProbe ioError =
                new TcpConnectProbe(hostname -> new InetAddress[] {v4}, (address, port, timeoutMs) -> {
                    throw new java.io.IOException("network unreachable");
                });
        assertEquals(
                TcpConnectOutcome.ERROR, ioError.probe("dns.example:80", 1.0).outcome());
    }

    @Test
    void ipv6OnlyHostnameUsesResolvedAddress() throws Exception {
        InetAddress v6 = InetAddress.getByName("2001:db8::2");
        TcpConnectProbe probe =
                new TcpConnectProbe(hostname -> new InetAddress[] {v6}, (address, port, timeoutMs) -> {});
        TcpConnectResult result = probe.probe("v6.example:443", 1.0);
        assertEquals(TcpConnectOutcome.SUCCESS, result.outcome());
        assertTrue(result.resolvedIp().contains("2001:db8"));
    }
}
