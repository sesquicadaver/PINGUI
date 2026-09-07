package io.pingui.persistence;

import io.pingui.monitor.CompletedPoll;
import io.pingui.monitor.QualityAlertEvent;
import io.pingui.monitor.RouteChangeEvent;
import java.util.List;
import java.util.Objects;

/**
 * Immutable history batch for one poll cycle (P35-007).
 *
 * <p>Applied on the session-persistence worker inside a single {@link SessionDatabase#inTransaction}
 * so probe threads never block on JDBC for route / poll_result / related events.
 *
 * @param poll completed poll aggregate (required for poll_result; may be failure)
 * @param probeErrorMessage optional {@code probe_error} text written before poll_result
 * @param routeChange optional route_change / baseline event after route upsert
 * @param qualityAlerts FIRING/RESOLVED edges for this cycle (may be empty)
 */
public record PollPersistenceBatch(
        CompletedPoll poll,
        String probeErrorMessage,
        RouteChangeEvent routeChange,
        List<QualityAlertEvent> qualityAlerts) {

    public PollPersistenceBatch {
        Objects.requireNonNull(poll, "poll");
        qualityAlerts = qualityAlerts == null ? List.of() : List.copyOf(qualityAlerts);
    }

    /** History for a successful or failed {@link CompletedPoll} without side events. */
    public static PollPersistenceBatch ofPoll(CompletedPoll poll) {
        return new PollPersistenceBatch(poll, null, null, List.of());
    }

    /** Failure path: probe_error event + failure {@link CompletedPoll} in one transaction. */
    public static PollPersistenceBatch ofFailure(CompletedPoll poll, String probeErrorMessage) {
        return new PollPersistenceBatch(poll, probeErrorMessage, null, List.of());
    }

    public PollPersistenceBatch withRouteChange(RouteChangeEvent event) {
        return new PollPersistenceBatch(poll, probeErrorMessage, event, qualityAlerts);
    }

    public PollPersistenceBatch withQualityAlerts(List<QualityAlertEvent> alerts) {
        return new PollPersistenceBatch(poll, probeErrorMessage, routeChange, alerts);
    }
}
