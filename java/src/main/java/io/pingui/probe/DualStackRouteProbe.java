package io.pingui.probe;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;
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
        if (requiresProcessTrace(targetHost)) {
            return processProbe.trace(targetHost, maxHops, timeoutSeconds);
        }
        return rawProbe.trace(targetHost, maxHops, timeoutSeconds);
    }

    /** IPv6 literals must use subprocess trace ({@code traceroute -6}). */
    static boolean requiresProcessTrace(String targetHost) {
        try {
            String normalized = HostAddressParser.normalize(targetHost);
            return HostAddressParser.kindOf(normalized) == HostAddressKind.IPV6;
        } catch (ConfigError ex) {
            return false;
        }
    }
}
