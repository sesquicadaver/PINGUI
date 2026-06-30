package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import java.util.List;

/** Result of polling one host during a monitoring cycle. */
public record HostPollOutcome(
        RouteSnapshot snapshot,
        String error,
        boolean routeChanged,
        List<String> oldIps,
        List<String> newIps,
        List<String> currentIps) {

    public static HostPollOutcome error(List<String> previousIps, String message) {
        return new HostPollOutcome(null, message, false, List.copyOf(previousIps), List.of(), List.copyOf(previousIps));
    }
}
