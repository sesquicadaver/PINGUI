package io.pingui.probe;

import io.pingui.probe.icmp.ProbeResult;
import java.io.IOException;
import java.util.Optional;

/** Probes a single hop (TTL) toward a target for MTR-style incremental tracing (P13-010). */
@FunctionalInterface
public interface MtrHopProber {
    /**
     * Sends one probe at {@code hop} (1-based TTL).
     *
     * @return empty when the hop timed out or was unreachable
     */
    Optional<ProbeResult> probeHop(String targetHost, String targetIp, int hop, double timeoutSeconds)
            throws IOException;

    /** Resolves the target IP for {@code targetHost}. */
    default String resolveTargetIp(String targetHost) throws IOException {
        return RawIcmpRouteProbe.resolveTargetIp(targetHost);
    }
}
