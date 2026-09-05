package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProbeOutcome;
import java.util.List;

/** Result of polling one host during a monitoring cycle (P32-001 / P32-003). */
public record HostPollOutcome(
        RouteSnapshot snapshot,
        String error,
        boolean routeChanged,
        List<String> oldIps,
        List<String> newIps,
        List<String> currentIps,
        PollSampleScope sampleScope,
        ProbeOutcome probeOutcome) {

    public HostPollOutcome {
        oldIps = List.copyOf(oldIps);
        newIps = List.copyOf(newIps);
        currentIps = List.copyOf(currentIps);
        sampleScope = sampleScope != null ? sampleScope : PollSampleScope.FULL;
        probeOutcome = probeOutcome != null ? probeOutcome : ProbeOutcome.NETWORK_ERROR;
    }

    public static HostPollOutcome error(List<String> previousIps, String message) {
        return error(previousIps, message, ProbeOutcome.NETWORK_ERROR);
    }

    public static HostPollOutcome error(List<String> previousIps, String message, ProbeOutcome outcome) {
        return new HostPollOutcome(
                null,
                message,
                false,
                List.copyOf(previousIps),
                List.of(),
                List.copyOf(previousIps),
                PollSampleScope.FULL,
                outcome != null ? outcome : ProbeOutcome.NETWORK_ERROR);
    }

    public static HostPollOutcome success(
            RouteSnapshot snapshot,
            boolean routeChanged,
            List<String> oldIps,
            List<String> newIps,
            List<String> currentIps,
            PollSampleScope sampleScope) {
        return success(snapshot, routeChanged, oldIps, newIps, currentIps, sampleScope, ProbeOutcome.SUCCESS);
    }

    public static HostPollOutcome success(
            RouteSnapshot snapshot,
            boolean routeChanged,
            List<String> oldIps,
            List<String> newIps,
            List<String> currentIps,
            PollSampleScope sampleScope,
            ProbeOutcome probeOutcome) {
        return new HostPollOutcome(snapshot, null, routeChanged, oldIps, newIps, currentIps, sampleScope, probeOutcome);
    }
}
