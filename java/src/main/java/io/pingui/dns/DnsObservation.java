package io.pingui.dns;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One forward-DNS sample for a hostname: address set, latency, and outcome (P29-004).
 *
 * @param host configured hostname (normalized)
 * @param outcome lookup class
 * @param addresses sorted unique address strings (empty on failure)
 * @param resolveMs wall time of the lookup attempt
 * @param observedAt sample time
 */
public record DnsObservation(
        String host, DnsLookupOutcome outcome, List<String> addresses, long resolveMs, Instant observedAt) {

    public DnsObservation {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(outcome, "outcome");
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
        Objects.requireNonNull(observedAt, "observedAt");
        if (resolveMs < 0) {
            resolveMs = 0;
        }
    }

    public boolean ok() {
        return outcome == DnsLookupOutcome.OK;
    }
}
