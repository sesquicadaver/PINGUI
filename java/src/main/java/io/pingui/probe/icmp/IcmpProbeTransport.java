package io.pingui.probe.icmp;

import java.io.IOException;

/** Injectable ICMP probe transport (production JNA or test doubles). */
public interface IcmpProbeTransport extends AutoCloseable {
    ProbeResult sendProbe(String targetIp, int ttl, double timeoutSeconds) throws IOException;

    @Override
    void close();
}
