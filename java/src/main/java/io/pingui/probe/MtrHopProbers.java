package io.pingui.probe;

import io.pingui.probe.icmp.LinuxJnaIcmpTransport;
import io.pingui.probe.icmp.RawIcmpPermission;
import java.io.IOException;
import java.util.Optional;

/** Factory for platform {@link MtrHopProber} instances (P13-011). */
public final class MtrHopProbers {
    private MtrHopProbers() {}

    /** Raw ICMP on Linux when permitted; otherwise a prober that fails with a clear I/O error. */
    public static MtrHopProber platformDefault() {
        if (LinuxJnaIcmpTransport.isLinux() && RawIcmpPermission.isAvailable()) {
            return new IcmpMtrHopProber();
        }
        return new MtrHopProber() {
            @Override
            public Optional<io.pingui.probe.icmp.ProbeResult> probeHop(
                    String targetHost, String targetIp, int hop, double timeoutSeconds) throws IOException {
                throw new IOException("MTR probe_mode requires Linux raw ICMP (CAP_NET_RAW or root)");
            }
        };
    }
}
