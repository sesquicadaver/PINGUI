package io.pingui.probe;

import java.io.IOException;

/**
 * One echo attempt at a fixed ICMP payload size for {@link MtuDiscovery} (P17-020).
 *
 * @return {@code true} if a reply was received
 */
@FunctionalInterface
public interface MtuProbeRunner {
    boolean pingOnce(String target, int payloadBytes, boolean ipv6, double timeoutSeconds) throws IOException;
}
