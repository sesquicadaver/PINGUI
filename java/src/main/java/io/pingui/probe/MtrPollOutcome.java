package io.pingui.probe;

import io.pingui.model.Models.RouteSnapshot;

/** Result of one MTR hop step (P13-010). */
public record MtrPollOutcome(RouteSnapshot snapshot, String error) {

    public static MtrPollOutcome ok(RouteSnapshot snapshot) {
        return new MtrPollOutcome(snapshot, null);
    }

    public static MtrPollOutcome failure(String message) {
        return new MtrPollOutcome(null, message);
    }
}
