package io.pingui.probe;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;

/**
 * Result of one MTR hop step (P13-010 / P32-001).
 *
 * <p>{@link #completeRoute()} is the accumulated path for UI; hop stats / telemetry must use only
 * {@link #freshHopSample()}. Endpoint alerts apply only when {@link #targetSampled()}.
 */
public record MtrPollOutcome(
        RouteSnapshot completeRoute,
        String error,
        MtrProbeState.Phase phase,
        int probedHop,
        HopNode freshHopSample,
        boolean targetSampled,
        MtrTargetOutcome targetOutcome,
        List<String> lastCompleteRouteIps) {

    public MtrPollOutcome {
        lastCompleteRouteIps = lastCompleteRouteIps == null ? List.of() : List.copyOf(lastCompleteRouteIps);
        if (error == null && completeRoute == null) {
            throw new IllegalArgumentException("completeRoute required when error is null");
        }
        if (targetOutcome == null) {
            targetOutcome = MtrTargetOutcome.NOT_SAMPLED;
        }
    }

    /** Backward-compatible accessor used by older call sites. */
    public RouteSnapshot snapshot() {
        return completeRoute;
    }

    public static MtrPollOutcome ok(
            RouteSnapshot completeRoute,
            MtrProbeState.Phase phase,
            int probedHop,
            HopNode freshHopSample,
            boolean targetSampled,
            MtrTargetOutcome targetOutcome,
            List<String> lastCompleteRouteIps) {
        return new MtrPollOutcome(
                completeRoute,
                null,
                phase,
                probedHop,
                freshHopSample,
                targetSampled,
                targetOutcome,
                lastCompleteRouteIps);
    }

    public static MtrPollOutcome failure(String message) {
        return new MtrPollOutcome(
                null,
                message,
                MtrProbeState.Phase.DISCOVERING,
                0,
                null,
                false,
                MtrTargetOutcome.NOT_SAMPLED,
                List.of());
    }
}
