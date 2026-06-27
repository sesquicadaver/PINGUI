package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.RouteProbe;
import java.io.IOException;
import java.util.List;

/** Pure polling logic for route monitoring (testable without UI). */
public final class RoutePoller {
    private final RouteProbe probe;

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
}
