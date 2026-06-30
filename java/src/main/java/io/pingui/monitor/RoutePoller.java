package io.pingui.monitor;

import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProcessHostPing;
import io.pingui.probe.RouteProbe;
import java.io.IOException;
import java.util.List;
import java.util.OptionalDouble;

/** Pure polling logic for route monitoring (testable without UI). */
public final class RoutePoller {
    private final RouteProbe probe;
    private final ProcessHostPing hostPing = new ProcessHostPing();

    public RoutePoller(RouteProbe probe) {
        this.probe = probe;
    }

    public HostPollOutcome pollHostRoute(
            String host, List<String> previousIps, int maxHops, double timeoutSeconds) {
        try {
            RouteSnapshot snapshot = probe.trace(host, maxHops, timeoutSeconds);
            List<String> currentIps = snapshot.routeIps();
            RouteChangeDetector.RouteChangeResult change =
                    RouteChangeDetector.detect(previousIps, currentIps);
            return new HostPollOutcome(
                    snapshot,
                    null,
                    change.changed(),
                    change.oldIps(),
                    change.newIps(),
                    currentIps);
        } catch (IOException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage());
        } catch (RuntimeException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage());
        }
    }

    /** Direct ping to target; single-hop snapshot, no traceroute. */
    public HostPollOutcome pollHostPingOnly(
            String host, List<String> previousIps, double timeoutSeconds, PingExpertEntry expert) {
        try {
            OptionalDouble rtt = hostPing.pingOnce(host, expert, timeoutSeconds);
            List<HopNode> nodes =
                    rtt.isPresent()
                            ? List.of(new HopNode(1, host, rtt.getAsDouble(), false))
                            : List.of(Models.timeout(1));
            RouteSnapshot snapshot = new RouteSnapshot(host, host, nodes);
            List<String> currentIps = snapshot.routeIps();
            RouteChangeDetector.RouteChangeResult change =
                    RouteChangeDetector.detect(previousIps, currentIps);
            return new HostPollOutcome(
                    snapshot,
                    null,
                    change.changed(),
                    change.oldIps(),
                    change.newIps(),
                    currentIps);
        } catch (IOException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage());
        } catch (RuntimeException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage());
        }
    }
}
