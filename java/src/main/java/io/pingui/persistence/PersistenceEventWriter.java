package io.pingui.persistence;

import io.pingui.dns.DnsControlEvent;
import io.pingui.monitor.QualityAlertEvent;
import io.pingui.monitor.RouteChangeEvent;
import java.time.Instant;
import java.util.Objects;

/** Writes discrete events to SQLite (P11-011); policy gate (P11-013 / P22-003 / P27-002). */
public final class PersistenceEventWriter {
    private final SessionDatabase database;
    private final PersistencePolicyHolder policyHolder;

    public PersistenceEventWriter(SessionDatabase database) {
        this(database, new PersistencePolicyHolder());
    }

    public PersistenceEventWriter(SessionDatabase database, PersistencePolicyHolder policyHolder) {
        this.database = Objects.requireNonNull(database, "database");
        this.policyHolder = policyHolder != null ? policyHolder : new PersistencePolicyHolder();
    }

    public PersistencePolicyHolder policyHolder() {
        return policyHolder;
    }

    public void writeRouteChange(RouteChangeEvent event) {
        if (event == null || !policyHolder.active().allows(PersistenceEventType.ROUTE_CHANGE)) {
            return;
        }
        ensureHostRow(event.host());
        database.insertEvent(
                PersistenceEventType.ROUTE_CHANGE,
                event.host(),
                event.profile(),
                null,
                null,
                PersistenceJson.stringArray(event.oldIps()),
                PersistenceJson.stringArray(event.newIps()),
                null,
                event.timestamp());
    }

    public boolean hasRouteChangeEvents(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return !database.listEvents(PersistenceEventType.ROUTE_CHANGE, host, Instant.EPOCH, 1)
                .isEmpty();
    }

    public void writeProbeError(String host, String message) {
        if (host == null || host.isBlank() || !policyHolder.active().allows(PersistenceEventType.PROBE_ERROR)) {
            return;
        }
        ensureHostRow(host);
        database.insertEvent(
                PersistenceEventType.PROBE_ERROR,
                host,
                null,
                null,
                message == null ? "" : message,
                null,
                null,
                null,
                Instant.now());
    }

    /**
     * Persists quality FIRING/RESOLVED ({@code endpoint_down} / {@code latency_high}). Survives UI ack;
     * default allowed when DB is connected.
     */
    public void writeQualityAlert(QualityAlertEvent event) {
        if (event == null) {
            return;
        }
        PersistenceEventType type = qualityEventType(event);
        if (!policyHolder.active().allows(type)) {
            return;
        }
        ensureHostRow(event.host());
        database.insertEvent(
                type,
                event.host(),
                event.profile(),
                event.state(),
                null,
                null,
                null,
                event.detailJson(),
                event.timestamp());
        syncIncident(event, type);
    }

    /** Persists administrator problem acknowledgment (P29-002 timeline / P30-002 incident ack). */
    public void writeProblemAck(String host, Instant when) {
        if (host == null || host.isBlank() || !policyHolder.active().allows(PersistenceEventType.PROBLEM_ACK)) {
            return;
        }
        Instant at = when != null ? when : Instant.now();
        ensureHostRow(host);
        database.insertEvent(PersistenceEventType.PROBLEM_ACK, host, null, null, null, null, null, null, at);
        database.acknowledgeOpenIncidents(host, at);
    }

    /**
     * Persists forward-DNS control observation (P29-004). Distinct event only — never opens a quality
     * incident or alert dispatch.
     */
    public void writeDnsChange(DnsControlEvent event) {
        if (event == null || !policyHolder.active().allows(PersistenceEventType.DNS_CHANGE)) {
            return;
        }
        ensureHostRow(event.host());
        database.insertEvent(
                PersistenceEventType.DNS_CHANGE,
                event.host(),
                null,
                event.state(),
                event.message(),
                PersistenceJson.stringArray(event.previousAddresses()),
                PersistenceJson.stringArray(event.addresses()),
                event.detailJson(),
                event.observedAt());
    }

    private static PersistenceEventType qualityEventType(QualityAlertEvent event) {
        if (QualityAlertEvent.EVENT_LATENCY_HIGH.equals(event.event())) {
            return PersistenceEventType.LATENCY_HIGH;
        }
        return PersistenceEventType.ENDPOINT_DOWN;
    }

    private void syncIncident(QualityAlertEvent event, PersistenceEventType type) {
        String kind = type.id();
        String severity = IncidentRecord.severityForKind(kind);
        if (QualityAlertEvent.STATE_FIRING.equals(event.state())) {
            database.openOrRefreshIncident(
                    event.host(), kind, severity, event.timestamp(), peakFromDetail(event), event.detailJson());
            return;
        }
        if (QualityAlertEvent.STATE_RESOLVED.equals(event.state())) {
            database.resolveIncident(event.host(), kind, event.timestamp());
        }
    }

    private static Double peakFromDetail(QualityAlertEvent event) {
        Object rtt = event.detail().get("rtt_ms");
        if (rtt instanceof Number number) {
            return number.doubleValue();
        }
        Object loss = event.detail().get("loss_percent");
        if (loss instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private void ensureHostRow(String host) {
        if (database.load(host) == null) {
            database.save(host, new io.pingui.model.Models.HostSessionData());
        }
    }

    /** Wire-shaped probe_error JSON for reconstructed {@link PersistenceEventRecord#payloadJson()}. */
    static String probeErrorPayload(String host, String message) {
        return "{\"message\":"
                + PersistenceJson.quote(message == null ? "" : message)
                + ",\"host\":"
                + PersistenceJson.quote(host)
                + "}";
    }

    /** Wire-shaped problem_ack JSON for reconstructed {@link PersistenceEventRecord#payloadJson()}. */
    static String problemAckPayload(String host, Instant when) {
        Instant at = when != null ? when : Instant.now();
        return "{\"event\":\"problem_ack\",\"host\":"
                + PersistenceJson.quote(host)
                + ",\"timestamp\":"
                + PersistenceJson.quote(at.toString())
                + '}';
    }

    /** Wire-shaped dns_change JSON for reconstructed {@link PersistenceEventRecord#payloadJson()}. */
    static String dnsChangePayload(String host, String state, String message, Instant when) {
        Instant at = when != null ? when : Instant.now();
        return "{\"event\":\"dns_change\",\"host\":"
                + PersistenceJson.quote(host)
                + ",\"state\":"
                + PersistenceJson.quote(state == null ? "" : state)
                + ",\"message\":"
                + PersistenceJson.quote(message == null ? "" : message)
                + ",\"timestamp\":"
                + PersistenceJson.quote(at.toString())
                + '}';
    }
}
