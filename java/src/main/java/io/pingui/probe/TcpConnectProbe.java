package io.pingui.probe;

import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;
import io.pingui.config.TcpEndpoint;
import io.pingui.dns.DnsControl;
import io.pingui.dns.DnsLookupOutcome;
import io.pingui.dns.DnsObservation;
import io.pingui.dns.ForwardDnsLookup;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Forward DNS + TCP connect probe for {@code host:port} targets (P29-005).
 *
 * <p>Complement to {@code ping_only}: measures connect latency / refusal / timeout, not ICMP RTT.
 */
public final class TcpConnectProbe {
    @FunctionalInterface
    public interface TcpDialer {
        /**
         * Opens a TCP connection to {@code address}:{@code port} within {@code timeoutMs}.
         *
         * @throws SocketTimeoutException on connect timeout
         * @throws ConnectException when the peer refuses
         * @throws IOException on other connect failures
         */
        void connect(InetAddress address, int port, int timeoutMs) throws IOException;
    }

    private final ForwardDnsLookup dnsLookup;
    private final TcpDialer dialer;

    public TcpConnectProbe() {
        this(DnsControl.systemLookup(), TcpConnectProbe::systemDial);
    }

    public TcpConnectProbe(ForwardDnsLookup dnsLookup, TcpDialer dialer) {
        this.dnsLookup = Objects.requireNonNull(dnsLookup, "dnsLookup");
        this.dialer = Objects.requireNonNull(dialer, "dialer");
    }

    /** Probes {@code host:port} with the given overall timeout (seconds). */
    public TcpConnectResult probe(String target, double timeoutSeconds) {
        TcpEndpoint endpoint = TcpEndpoint.parse(target);
        int timeoutMs = Math.max(1, (int) Math.round(Math.max(0.001, timeoutSeconds) * 1000.0));
        Instant at = Instant.now();
        InetAddress address;
        long dnsMs;
        if (HostAddressParser.kindOf(endpoint.host()) != HostAddressKind.HOSTNAME) {
            try {
                address = InetAddress.getByName(endpoint.host());
            } catch (UnknownHostException ex) {
                return new TcpConnectResult(
                        endpoint.display(), "", 0L, 0L, TcpConnectOutcome.DNS_ERROR, "DNS error: " + ex.getMessage());
            }
            dnsMs = 0L;
        } else {
            DnsObservation dns = DnsControl.lookup(endpoint.host(), dnsLookup, at);
            if (dns.outcome() != DnsLookupOutcome.OK || dns.addresses().isEmpty()) {
                return new TcpConnectResult(
                        endpoint.display(),
                        "",
                        dns.resolveMs(),
                        0L,
                        TcpConnectOutcome.DNS_ERROR,
                        "DNS " + dns.outcome().id() + " (" + dns.resolveMs() + "ms)");
            }
            try {
                address = pickAddress(dns.addresses());
            } catch (UnknownHostException ex) {
                return new TcpConnectResult(
                        endpoint.display(),
                        "",
                        dns.resolveMs(),
                        0L,
                        TcpConnectOutcome.DNS_ERROR,
                        "DNS error: " + ex.getMessage());
            }
            dnsMs = dns.resolveMs();
        }
        String resolved = address.getHostAddress();
        if (address instanceof Inet6Address) {
            int zone = resolved.indexOf('%');
            if (zone >= 0) {
                resolved = resolved.substring(0, zone);
            }
            resolved = resolved.toLowerCase(Locale.ROOT);
        }
        long connectStarted = System.nanoTime();
        try {
            dialer.connect(address, endpoint.port(), timeoutMs);
            long connectMs = elapsedMs(connectStarted);
            return new TcpConnectResult(
                    endpoint.display(),
                    resolved,
                    dnsMs,
                    connectMs,
                    TcpConnectOutcome.SUCCESS,
                    "connected in " + connectMs + "ms (dns " + dnsMs + "ms)");
        } catch (SocketTimeoutException ex) {
            return new TcpConnectResult(
                    endpoint.display(),
                    resolved,
                    dnsMs,
                    elapsedMs(connectStarted),
                    TcpConnectOutcome.TIMEOUT,
                    "TCP timeout (" + elapsedMs(connectStarted) + "ms, dns " + dnsMs + "ms)");
        } catch (ConnectException ex) {
            return new TcpConnectResult(
                    endpoint.display(),
                    resolved,
                    dnsMs,
                    elapsedMs(connectStarted),
                    TcpConnectOutcome.REFUSED,
                    "TCP refused (" + elapsedMs(connectStarted) + "ms, dns " + dnsMs + "ms)");
        } catch (IOException ex) {
            return new TcpConnectResult(
                    endpoint.display(),
                    resolved,
                    dnsMs,
                    elapsedMs(connectStarted),
                    TcpConnectOutcome.ERROR,
                    "TCP error: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    static void systemDial(InetAddress address, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeoutMs);
        }
    }

    private static InetAddress pickAddress(java.util.List<String> addresses) throws UnknownHostException {
        InetAddress fallback = null;
        for (String text : addresses) {
            InetAddress parsed = InetAddress.getByName(text);
            if (parsed instanceof Inet4Address) {
                return parsed;
            }
            if (fallback == null) {
                fallback = parsed;
            }
        }
        if (fallback == null) {
            throw new UnknownHostException("no addresses");
        }
        return fallback;
    }

    private static long elapsedMs(long startedNanos) {
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return ms < 0 ? 0 : ms;
    }
}
