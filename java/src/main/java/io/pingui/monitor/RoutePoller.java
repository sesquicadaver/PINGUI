package io.pingui.monitor;

import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.MtrPollOutcome;
import io.pingui.probe.MtrProbe;
import io.pingui.probe.MtrProbeState;
import io.pingui.probe.ProbeOutcome;
import io.pingui.probe.ProcessHostPing;
import io.pingui.probe.RouteProbe;
import io.pingui.probe.TcpConnectProbe;
import io.pingui.probe.TcpConnectResult;
import java.io.IOException;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/** Pure polling logic for route monitoring (testable without UI). */
public final class RoutePoller {
    private final RouteProbe probe;
    private final ProcessHostPing hostPing = new ProcessHostPing();
    private final MtrProbe mtrProbe;
    private final TcpConnectProbe tcpConnectProbe;
    /** Per-host candidate-route FSM shared by TRACE and MTR (P34-001). */
    private final ConcurrentHashMap<String, CandidateRouteFsm> routeFsms = new ConcurrentHashMap<>();

    public RoutePoller(RouteProbe probe) {
        this(probe, null);
    }

    public RoutePoller(RouteProbe probe, MtrProbe mtrProbe) {
        this(probe, mtrProbe, new TcpConnectProbe());
    }

    RoutePoller(RouteProbe probe, MtrProbe mtrProbe, TcpConnectProbe tcpConnectProbe) {
        this.probe = probe;
        this.mtrProbe = mtrProbe;
        this.tcpConnectProbe = tcpConnectProbe != null ? tcpConnectProbe : new TcpConnectProbe();
    }

    /**
     * Incremental MTR-style poll: one hop per call (P13-010 / P32-001 / P34-001).
     *
     * <p>Discovery prefix growth and hop timeouts are not route-change events. Topology alerts fire
     * once when a candidate path is confirmed to the target.
     */
    public HostPollOutcome pollHostMtr(String host, List<String> previousIps, int maxHops, double timeoutSeconds) {
        if (mtrProbe == null) {
            return HostPollOutcome.error(previousIps, "MTR probe not configured");
        }
        MtrPollOutcome outcome = mtrProbe.poll(host, maxHops, timeoutSeconds);
        if (outcome.error() != null) {
            return HostPollOutcome.error(previousIps, outcome.error());
        }
        RouteSnapshot snapshot = outcome.completeRoute();
        List<String> currentIps = snapshot.routeIps();
        boolean targetConfirmed = outcome.targetSampled() && outcome.phase() == MtrProbeState.Phase.MONITORING;
        RouteChangeDetector.RouteChangeResult change =
                RouteChangeDetector.observe(fsmFor(host), snapshot, targetConfirmed, previousIps);
        PollSampleScope scope = outcome.probedHop() >= 1
                ? PollSampleScope.mtr(outcome.probedHop(), outcome.targetSampled())
                : PollSampleScope.UNSAMPLED;
        return HostPollOutcome.success(
                snapshot,
                change.changed(),
                change.oldIps(),
                change.newIps(),
                currentIps,
                scope,
                icmpOrMtrOutcome(snapshot, scope));
    }

    /**
     * @deprecated Prefer candidate FSM via {@link #pollHostMtr}; kept for unit tests of shrink helper.
     */
    static boolean detectMtrRouteChange(MtrPollOutcome outcome, List<String> currentIps) {
        List<String> baseline = outcome.lastCompleteRouteIps();
        if (baseline == null || baseline.isEmpty()) {
            return false;
        }
        if (outcome.phase() == MtrProbeState.Phase.DISCOVERING) {
            // Mid-discovery rewrite is candidate-only (P34-001); confirm on target.
            return false;
        }
        HopNode fresh = outcome.freshHopSample();
        if (fresh != null && !fresh.isReachable()) {
            return false;
        }
        if (baseline.equals(currentIps)) {
            return false;
        }
        if (isTimeoutOnlyShrink(baseline, currentIps)) {
            return false;
        }
        return outcome.targetSampled();
    }

    static boolean isTimeoutOnlyShrink(List<String> baseline, List<String> current) {
        if (current.size() >= baseline.size()) {
            return false;
        }
        return baseline.subList(0, current.size()).equals(current);
    }

    public void resetMtrHost(String host) {
        resetRouteIdentity(host);
        if (mtrProbe != null) {
            mtrProbe.resetHost(host);
        }
    }

    /** Clears MTR state on host rename (P32-002); new name rediscovers from scratch. */
    public void renameMtrHost(String oldHost, String newHost) {
        resetRouteIdentity(oldHost);
        if (newHost != null && !newHost.equals(oldHost)) {
            resetRouteIdentity(newHost);
        }
        if (mtrProbe != null) {
            mtrProbe.renameHost(oldHost, newHost);
        }
    }

    /** Drops candidate-route FSM for {@code host} (mode change / remove / rename). */
    public void resetRouteIdentity(String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        CandidateRouteFsm removed = routeFsms.remove(host);
        if (removed != null) {
            removed.reset();
        }
    }

    CandidateRouteFsm fsmFor(String host) {
        return routeFsms.computeIfAbsent(host, ignored -> new CandidateRouteFsm());
    }

    public HostPollOutcome pollHostRoute(String host, List<String> previousIps, int maxHops, double timeoutSeconds) {
        try {
            RouteSnapshot snapshot = probe.trace(host, maxHops, timeoutSeconds);
            List<String> currentIps = snapshot.routeIps();
            RouteChangeDetector.RouteChangeResult change = RouteChangeDetector.observe(
                    fsmFor(host), snapshot, RouteChangeDetector.targetReached(snapshot), previousIps);
            return HostPollOutcome.success(
                    snapshot,
                    change.changed(),
                    change.oldIps(),
                    change.newIps(),
                    currentIps,
                    PollSampleScope.FULL,
                    icmpOrMtrOutcome(snapshot, PollSampleScope.FULL));
        } catch (IOException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage(), ProbeOutcome.NETWORK_ERROR);
        } catch (RuntimeException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage(), ProbeOutcome.NETWORK_ERROR);
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
            RouteChangeDetector.RouteChangeResult change = RouteChangeDetector.observe(
                    fsmFor(host), snapshot, RouteChangeDetector.targetReached(snapshot), previousIps);
            return HostPollOutcome.success(
                    snapshot,
                    change.changed(),
                    change.oldIps(),
                    change.newIps(),
                    currentIps,
                    PollSampleScope.FULL,
                    icmpOrMtrOutcome(snapshot, PollSampleScope.FULL));
        } catch (IOException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage(), ProbeOutcome.NETWORK_ERROR);
        } catch (RuntimeException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage(), ProbeOutcome.NETWORK_ERROR);
        }
    }

    /**
     * TCP connect to {@code host:port}: DNS time + connect time with structured {@link ProbeOutcome}
     * (P29-005 / P32-003). Not an ICMP replacement.
     */
    public HostPollOutcome pollHostTcpConnect(String host, List<String> previousIps, double timeoutSeconds) {
        try {
            TcpConnectResult result = tcpConnectProbe.probe(host, timeoutSeconds);
            ProbeOutcome outcome = ProbeOutcome.fromTcp(result.outcome());
            if (outcome == ProbeOutcome.DNS_ERROR || outcome == ProbeOutcome.NETWORK_ERROR) {
                return HostPollOutcome.error(previousIps, result.message(), outcome);
            }
            List<HopNode> nodes;
            String targetIp;
            if (result.success()) {
                targetIp = result.resolvedIp();
                nodes = List.of(new HopNode(1, targetIp, (double) result.connectMs(), false));
            } else {
                targetIp = result.resolvedIp().isBlank() ? host : result.resolvedIp();
                nodes = List.of(Models.timeout(1));
            }
            RouteSnapshot snapshot = new RouteSnapshot(host, targetIp, nodes);
            List<String> currentIps = snapshot.routeIps();
            RouteChangeDetector.RouteChangeResult change = RouteChangeDetector.observe(
                    fsmFor(host), snapshot, RouteChangeDetector.targetReached(snapshot), previousIps);
            return HostPollOutcome.success(
                    snapshot,
                    change.changed(),
                    change.oldIps(),
                    change.newIps(),
                    currentIps,
                    PollSampleScope.FULL,
                    outcome);
        } catch (RuntimeException ex) {
            return HostPollOutcome.error(previousIps, ex.getMessage(), ProbeOutcome.NETWORK_ERROR);
        }
    }

    /** ICMP / MTR: reachable target → SUCCESS; otherwise TIMEOUT (not synthetic loss). */
    static ProbeOutcome icmpOrMtrOutcome(RouteSnapshot snapshot, PollSampleScope scope) {
        if (scope != null && !scope.targetSampled()) {
            return ProbeOutcome.SUCCESS;
        }
        return TelemetryEmission.isTargetReachable(snapshot) ? ProbeOutcome.SUCCESS : ProbeOutcome.TIMEOUT;
    }
}
