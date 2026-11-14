package io.pingui.probe;

import io.pingui.config.ConfigError;
import io.pingui.model.Models.RouteSnapshot;
import java.io.IOException;

/**
 * AUTO probe: raw ICMP for IPv4/hostname; subprocess traceroute for IPv6 literals (V6-044).
 * Raw ICMP supports IPv6 literals when {@code probe: raw} ({@link io.pingui.probe.icmp.LinuxJnaIcmpTransport}).
 */
public final class DualStackRouteProbe implements RouteProbe {
    private final RouteProbe rawProbe;
    private final RouteProbe processProbe;

    public DualStackRouteProbe(RouteProbe rawProbe, RouteProbe processProbe) {
        this.rawProbe = rawProbe;
        this.processProbe = processProbe;
    }

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) throws IOException {
        try {
            if (requiresProcessTrace(targetHost)) {
                return processProbe.trace(targetHost, maxHops, timeoutSeconds);
            }
        } catch (ConfigError ex) {
            throw new IOException("Invalid trace target: " + targetHost, ex);
        }
        return rawProbe.trace(targetHost, maxHops, timeoutSeconds);
    }

    /**
     * IPv6 literals must use subprocess trace ({@code traceroute -6}).
     *
     * @throws ConfigError if {@code targetHost} is not a valid host entry (fail-closed; do not send to raw)
     */
    static boolean requiresProcessTrace(String targetHost) {
        return AddressFamilyPolicy.requiresProcessTrace(targetHost);
    }
}
