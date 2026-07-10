package io.pingui.probe;

import io.pingui.probe.icmp.IcmpProbeTransport;
import io.pingui.probe.icmp.LinuxJnaIcmpTransport;
import io.pingui.probe.icmp.ProbeResult;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

/** Linux raw-ICMP single-hop prober for {@link MtrProbe} (P13-010/011). */
public final class IcmpMtrHopProber implements MtrHopProber {
    private final Supplier<IcmpProbeTransport> transportFactory;

    public IcmpMtrHopProber() {
        this(() -> {
            try {
                return openTransport();
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
    }

    IcmpMtrHopProber(Supplier<IcmpProbeTransport> transportFactory) {
        this.transportFactory = transportFactory;
    }

    private static IcmpProbeTransport openTransport() throws IOException {
        return LinuxJnaIcmpTransport.open();
    }

    @Override
    public String resolveTargetIp(String targetHost) throws IOException {
        return RawIcmpRouteProbe.resolveTargetIp(targetHost);
    }

    @Override
    public Optional<ProbeResult> probeHop(String targetHost, String targetIp, int hop, double timeoutSeconds)
            throws IOException {
        try (IcmpProbeTransport transport = transportFactory.get()) {
            ProbeResult result = transport.sendProbe(targetIp, hop, timeoutSeconds);
            return Optional.ofNullable(result);
        }
    }
}
