package io.pingui.monitor;

/**
 * Maps endpoint/route/problem/timeline/alert signals onto {@link Severity} (P31-004).
 *
 * <p>Red ({@link Severity#CRITICAL}) is reserved for real endpoint unavailability — not route
 * change or missing path data.
 */
public final class SeverityClassifier {
    private SeverityClassifier() {}

    /**
     * Live host-row severity from monitoring state.
     *
     * <p>Unread quality problems take priority over soft endpoint/route signals.
     */
    public static Severity forHost(
            boolean enabled, EndpointState endpoint, RouteState route, HostProblemSummary problem) {
        if (!enabled) {
            return Severity.MUTED;
        }
        if (problem != null && problem.showBadge()) {
            if (isEndpointDownRule(problem)) {
                return Severity.CRITICAL;
            }
            if (isLatencyHighRule(problem)) {
                return Severity.WARNING;
            }
            return Severity.WARNING;
        }
        EndpointState ep = endpoint != null ? endpoint : EndpointState.UNKNOWN;
        RouteState rt = route != null ? route : RouteState.NOT_TRACED;
        if (ep == EndpointState.DOWN) {
            return Severity.CRITICAL;
        }
        if (ep == EndpointState.DEGRADED) {
            return Severity.WARNING;
        }
        if (rt == RouteState.CHANGED) {
            return Severity.NOTICE;
        }
        if (ep == EndpointState.UNKNOWN) {
            return Severity.MUTED;
        }
        if (rt == RouteState.INCOMPLETE) {
            return Severity.INFO;
        }
        return Severity.INFO;
    }

    /** Timeline row severity from kind + optional quality state ({@code firing}/{@code resolved}). */
    public static Severity forTimeline(IncidentTimelineKind kind, String state) {
        IncidentTimelineKind safe = kind != null ? kind : IncidentTimelineKind.PROBE_ERROR;
        boolean firing = state != null && HostProblemSummary.STATE_FIRING.equalsIgnoreCase(state.strip());
        return switch (safe) {
            case ENDPOINT_DOWN -> firing ? Severity.CRITICAL : Severity.INFO;
            case LATENCY_HIGH -> firing ? Severity.WARNING : Severity.INFO;
            case ROUTE_CHANGE -> Severity.NOTICE;
            case PROBE_ERROR -> Severity.WARNING;
            case PROBLEM_ACK, DNS_CHANGE -> Severity.INFO;
        };
    }

    /** Alert channel severity from ADR event type string. */
    public static Severity forAlertEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return Severity.INFO;
        }
        String normalized = eventType.strip().toLowerCase();
        return switch (normalized) {
            case "endpoint_down" -> Severity.CRITICAL;
            case "latency_high" -> Severity.WARNING;
            case "route_change" -> Severity.NOTICE;
            default -> Severity.INFO;
        };
    }

    static boolean isEndpointDownRule(HostProblemSummary problem) {
        return problem != null
                && (QualityAlertEvent.EVENT_ENDPOINT_DOWN.equals(problem.rule())
                        || (problem.description() != null
                                && problem.description().startsWith("endpoint_down")));
    }

    static boolean isLatencyHighRule(HostProblemSummary problem) {
        return problem != null
                && (QualityAlertEvent.EVENT_LATENCY_HIGH.equals(problem.rule())
                        || (problem.description() != null
                                && problem.description().startsWith("latency_high")));
    }
}
