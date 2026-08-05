package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.telemetry.MetricNames;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryBus;
import io.pingui.telemetry.TelemetryEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Non-blocking telemetry offers after poll outcomes (P16-013). */
final class TelemetryEmission {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryEmission.class);

    private volatile TelemetryBus telemetryBus;
    private volatile String alertProfileName = "default";

    void setTelemetryBus(TelemetryBus telemetryBus) {
        this.telemetryBus = telemetryBus;
    }

    void setAlertProfileName(String alertProfileName) {
        if (alertProfileName == null || alertProfileName.isBlank()) {
            this.alertProfileName = "default";
        } else {
            this.alertProfileName = alertProfileName;
        }
    }

    void clear() {
        telemetryBus = null;
    }

    void offerSuccess(String host, HostProbeMode probeMode, RouteSnapshot snapshot, double durationMs) {
        TelemetryBus bus = telemetryBus;
        if (bus == null) {
            return;
        }
        Instant ts = Instant.now();
        Map<String, String> labels = telemetryLabels(probeMode);
        try {
            bus.offerSample(new MetricSample(
                    MetricNames.TARGET_REACHABLE, isTargetReachable(snapshot) ? 1.0 : 0.0, host, null, labels, ts));
            bus.offerSample(new MetricSample(MetricNames.TRACE_DURATION_MS, durationMs, host, null, labels, ts));
            for (HopNode node : snapshot.nodes()) {
                double lossPct = node.isReachable() && node.pingMs() != null ? 0.0 : 100.0;
                bus.offerSample(new MetricSample(MetricNames.HOP_LOSS_PCT, lossPct, host, node.hop(), labels, ts));
                if (node.pingMs() != null && node.isReachable()) {
                    bus.offerSample(MetricSample.rttMs(host, node.hop(), node.pingMs(), labels, ts));
                }
            }
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry sample offer failed for {}: {}", host, ex.getMessage());
        }
    }

    void offerFailure(String host, String message, HostProbeMode probeMode, double durationMs) {
        TelemetryBus bus = telemetryBus;
        if (bus == null) {
            return;
        }
        Instant ts = Instant.now();
        Map<String, String> labels = telemetryLabels(probeMode);
        try {
            // No TARGET_REACHABLE sample: probe_error sets unreachable without clearHostRtt (P15 parity).
            bus.offerSample(new MetricSample(MetricNames.TRACE_DURATION_MS, durationMs, host, null, labels, ts));
            bus.offerEvent(TelemetryEvent.probeError(host, message, labels, ts));
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry failure offer failed for {}: {}", host, ex.getMessage());
        }
    }

    void offerRouteChange(String host, List<String> oldIps, List<String> newIps, HostProbeMode probeMode) {
        TelemetryBus bus = telemetryBus;
        if (bus == null) {
            return;
        }
        try {
            bus.offerEvent(TelemetryEvent.routeChange(host, oldIps, newIps, telemetryLabels(probeMode), Instant.now()));
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry route_change offer failed for {}: {}", host, ex.getMessage());
        }
    }

    /** Target reachable when a hop IP matches {@code targetIp}, else any reachable hop. */
    static boolean isTargetReachable(RouteSnapshot snapshot) {
        String targetIp = snapshot.targetIp();
        if (targetIp != null && !targetIp.isBlank()) {
            for (var node : snapshot.nodes()) {
                if (node.isReachable() && targetIp.equals(node.ip())) {
                    return true;
                }
            }
            return false;
        }
        for (var node : snapshot.nodes()) {
            if (node.isReachable()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> telemetryLabels(HostProbeMode probeMode) {
        return MetricNames.javaLabels(alertProfileName, probeMode.yamlValue());
    }
}
