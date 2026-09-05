package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import java.util.List;

/** Result of polling one host during a monitoring cycle (P32-001 sample scope). */
public record HostPollOutcome(
        RouteSnapshot snapshot,
        String error,
        boolean routeChanged,
        List<String> oldIps,
        List<String> newIps,
        List<String> currentIps,
        PollSampleScope sampleScope) {

    public HostPollOutcome {
        oldIps = List.copyOf(oldIps);
        newIps = List.copyOf(newIps);
        currentIps = List.copyOf(currentIps);
        sampleScope = sampleScope != null ? sampleScope : PollSampleScope.FULL;
    }

    public static HostPollOutcome error(List<String> previousIps, String message) {
        return new HostPollOutcome(
                null,
                message,
                false,
                List.copyOf(previousIps),
                List.of(),
                List.copyOf(previousIps),
                PollSampleScope.FULL);
    }

    public static HostPollOutcome success(
            RouteSnapshot snapshot,
            boolean routeChanged,
            List<String> oldIps,
            List<String> newIps,
            List<String> currentIps,
            PollSampleScope sampleScope) {
        return new HostPollOutcome(snapshot, null, routeChanged, oldIps, newIps, currentIps, sampleScope);
    }
}
