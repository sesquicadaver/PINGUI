package io.pingui.observability;

import io.pingui.telemetry.MetricNames;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import io.pingui.telemetry.TelemetrySink;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-process Prometheus scrape sink (P16-051 / ADR_TELEMETRY / ADR_OBSERVABILITY).
 *
 * <p>Applies bus {@link MetricSample}/{@link TelemetryEvent} onto a shared {@link PrometheusExporter}.
 * Not a remote_write client — scrape remains {@code GET /metrics}. {@link #eventsOnly()} is {@code
 * false} so hop RTT gauges update. Failures are logged; methods never throw into the bus path.
 *
 * <p>Does not close the exporter (owned by {@link MetricsHttpServer} / daemon).
 */
public final class PrometheusTelemetrySink implements TelemetrySink {
    public static final String ID = "prometheus";

    private static final Logger LOG = Logger.getLogger(PrometheusTelemetrySink.class.getName());

    private final PrometheusExporter exporter;

    public PrometheusTelemetrySink(PrometheusExporter exporter) {
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    /** Shared scrape state holder. */
    public PrometheusExporter exporter() {
        return exporter;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean eventsOnly() {
        return false;
    }

    @Override
    public void onSample(MetricSample sample) {
        if (sample == null) {
            return;
        }
        try {
            applySample(sample);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "PrometheusTelemetrySink sample failed for " + sample.host(), ex);
        }
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        try {
            applyEvent(event);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "PrometheusTelemetrySink event failed for " + event.host(), ex);
        }
    }

    private void applySample(MetricSample sample) {
        String name = sample.name();
        String host = sample.host();
        if (MetricNames.TARGET_REACHABLE.equals(name)) {
            // Success poll cycle marker: drop stale hops before following RTT samples.
            exporter.clearHostRtt(host);
            exporter.recordReachable(host, sample.value() >= 0.5);
            return;
        }
        if (MetricNames.TRACE_DURATION_MS.equals(name)) {
            String probeMode = sample.labels().get(MetricNames.LABEL_PROBE_MODE);
            if (probeMode == null || probeMode.isBlank()) {
                probeMode = "trace";
            }
            exporter.recordTraceDuration(host, probeMode, sample.value());
            return;
        }
        if (MetricNames.RTT_MS.equals(name)) {
            Integer hop = sample.hop();
            if (hop == null || hop < 1) {
                return;
            }
            exporter.recordRtt(host, hop, sample.value());
            return;
        }
        // HOP_LOSS_PCT and other samples are not part of the Prometheus scrape contract.
    }

    private void applyEvent(TelemetryEvent event) {
        if (TelemetryEvent.ROUTE_CHANGE.equals(event.event())) {
            if (!event.oldIps().isEmpty()) {
                exporter.incrementRouteChange(event.host());
            }
            return;
        }
        if (TelemetryEvent.PROBE_ERROR.equals(event.event())) {
            exporter.recordReachable(event.host(), false);
        }
    }
}
