package io.pingui.probe;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.icmp.IcmpProbeTransport;
import io.pingui.probe.icmp.LinuxJnaIcmpTransport;
import io.pingui.probe.icmp.ProbeResult;
import java.io.IOException;
import java.net.InetAddress;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Linux raw ICMP traceroute (TTL 1..N) for parity with Python scapy.
 * Requires CAP_NET_RAW or root.
 */
public final class RawIcmpRouteProbe implements RouteProbe {
    private final Supplier<IcmpProbeTransport> transportFactory;

    public RawIcmpRouteProbe() {
        this(() -> openTransport());
    }

    private static IcmpProbeTransport openTransport() {
        try {
            return LinuxJnaIcmpTransport.open();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    RawIcmpRouteProbe(Supplier<IcmpProbeTransport> transportFactory) {
        this.transportFactory = transportFactory;
    }

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) throws IOException {
        String targetIp = resolveTargetIp(targetHost);
        List<HopNode> nodes = new ArrayList<>();
        try (IcmpProbeTransport transport = transportFactory.get()) {
            for (int ttl = 1; ttl <= maxHops; ttl++) {
                ProbeResult result = transport.sendProbe(targetIp, ttl, timeoutSeconds);
                if (result == null) {
                    nodes.add(Models.timeout(ttl));
                    continue;
                }
                nodes.add(new HopNode(ttl, result.sourceIp(), result.rttMs(), false));
                if (result.target()) {
                    break;
                }
            }
        }
        if (nodes.isEmpty()) {
            throw new IOException("No hops collected for " + targetHost);
        }
        return new RouteSnapshot(targetHost, targetIp, nodes);
    }

    static String resolveTargetIp(String targetHost) throws IOException {
        try {
            return InetAddress.getByName(targetHost).getHostAddress();
        } catch (Exception ex) {
            throw new IOException("Cannot resolve host: " + targetHost, ex);
        }
    }
}
