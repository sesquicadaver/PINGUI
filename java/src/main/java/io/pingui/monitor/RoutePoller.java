package io.pingui.monitor;

import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.MtrPollOutcome;
import io.pingui.probe.MtrProbe;
import io.pingui.probe.ProcessHostPing;
import io.pingui.probe.RouteProbe;
import java.io.IOException;
import java.util.List;
import java.util.OptionalDouble;

/** Pure polling logic for route monitoring (testable without UI). */
public final class RoutePoller {
    private final RouteProbe probe;
    private final ProcessHostPing hostPing = new ProcessHostPing();
    private final MtrProbe mtrProbe;

    public RoutePoller(RouteProbe probe) {
        this(probe, null);
    }

    public RoutePoller(RouteProbe probe, MtrProbe mtrProbe) {
        this.probe = probe;
        this.mtrProbe = mtrProbe;
    }

    /** Incremental MTR-style poll: one hop per call (P13-010). */
    public HostPollOutcome pollHostMtr(String host, List<String> previousIps, int maxHops, double timeoutSeconds) {
        if (mtrProbe == null) {
            return HostPollOutcome.error(previousIps, "MTR probe not configured");
        }
        MtrPollOutcome outcome = mtrProbe.poll(host, maxHops, timeoutSeconds);
        if (outcome.error() != null) {
            return HostPollOutcome.error(previousIps, outcome.error());
        }
        RouteSnapshot snapshot = outcome.snapshot();
        List<String> currentIps = snapshot.routeIps();
        RouteChangeDetector.RouteChangeResult change = RouteChangeDetector.detect(previousIps, currentIps);
        return new HostPollOutcome(snapshot, null, change.changed(), change.oldIps(), change.newIps(), currentIps);
    }

    public HostPollOutcome pollHostRoute(String host, List<String> previousIps, int maxHops, double timeoutSeconds) {
        try {
            RouteSnapshot snapshot = probe.trace(host, maxHops, timeoutSeconds);
            List<String> currentIps = snapshot.routeIps();
            RouteChangeDetector.RouteChangeResult change = RouteChangeDetector.detect(previousIps, currentIps);
            return new HostPollOutcome(snapshot, null, change.changed(), change.oldIps(), change.newIps(), currentIps);
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
            List<HopNode> nodes = rtt.isPresent()
                    ? List.of(new HopNode(1, host, rtt.getAsDouble(), false))
                    : List.of(Models.timeout(1));
            RouteSnapshot snapshot = new RouteSnapshot(host, host, nodes);
            List<String> currentIps = snapshot.routeIps();
            RouteChangeDetector.RouteChangeResult change = RouteChangeDetector.detect(previousIps, currentIps);
            return new HostPollOutcome(snapshot, null, change.changed(), change.oldIps(), change.newIps(), currentIps);
        } catch (IOException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage());
        } catch (RuntimeException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage());
        }
    }
}
