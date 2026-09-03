package io.pingui.persistence;

import io.pingui.monitor.QualityAlertEvent;
import io.pingui.monitor.RouteChangeEvent;
import java.time.Instant;
import java.util.Objects;

/** One row from {@code persistence_event} (P11-020 / P27-002 typed columns). */
public record PersistenceEventRecord(
        long id,
        PersistenceEventType eventType,
        String host,
        String profile,
        String state,
        String message,
        String oldIpsJson,
        String newIpsJson,
        String detailJson,
        Instant observedAt) {

    public PersistenceEventRecord {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(observedAt, "observedAt");
    }

    /**
     * Rebuilds the historical wire JSON for UI/tests (not stored). Host/profile/time come from
     * columns.
     */
    public String payloadJson() {
        return switch (eventType) {
            case ROUTE_CHANGE -> new RouteChangeEvent(
                            host,
                            PersistenceJson.parseStringArray(oldIpsJson),
                            PersistenceJson.parseStringArray(newIpsJson),
                            observedAt,
                            profile)
                    .toJson();
            case PROBE_ERROR -> PersistenceEventWriter.probeErrorPayload(host, message);
            case ENDPOINT_DOWN, LATENCY_HIGH -> qualityPayload();
            case PROBLEM_ACK -> PersistenceEventWriter.problemAckPayload(host, observedAt);
            case DNS_CHANGE -> PersistenceEventWriter.dnsChangePayload(host, state, message, observedAt);
        };
    }

    private String qualityPayload() {
        String eventName = eventType == PersistenceEventType.LATENCY_HIGH
                ? QualityAlertEvent.EVENT_LATENCY_HIGH
                : QualityAlertEvent.EVENT_ENDPOINT_DOWN;
        String profileValue = profile == null || profile.isBlank() ? "default" : profile;
        String stateValue = state == null || state.isBlank() ? QualityAlertEvent.STATE_FIRING : state;
        String detail = detailJson == null || detailJson.isBlank() ? "{}" : detailJson;
        return "{\"event\":"
                + PersistenceJson.quote(eventName)
                + ",\"state\":"
                + PersistenceJson.quote(stateValue)
                + ",\"host\":"
                + PersistenceJson.quote(host)
                + ",\"timestamp\":"
                + PersistenceJson.quote(observedAt.toString())
                + ",\"profile\":"
                + PersistenceJson.quote(profileValue)
                + ",\"rule\":"
                + PersistenceJson.quote(eventName)
                + ",\"detail\":"
                + detail
                + '}';
    }
}
