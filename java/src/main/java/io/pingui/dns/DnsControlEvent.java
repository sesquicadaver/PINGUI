package io.pingui.dns;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Discrete DNS-control persistence signal (P29-004). Not a quality/alert incident.
 *
 * @param host hostname
 * @param state wire state ({@code ok}, {@code change}, {@code nxdomain}, {@code timeout}, {@code
 *     servfail}, {@code error})
 * @param message human detail including resolve time / address summary
 * @param previousAddresses prior address set (empty on first sample)
 * @param addresses current address set (empty on failure)
 * @param resolveMs lookup latency
 * @param outcome raw lookup outcome
 * @param observedAt event time
 */
public record DnsControlEvent(
        String host,
        String state,
        String message,
        List<String> previousAddresses,
        List<String> addresses,
        long resolveMs,
        DnsLookupOutcome outcome,
        Instant observedAt) {

    public DnsControlEvent {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");
        previousAddresses = previousAddresses == null ? List.of() : List.copyOf(previousAddresses);
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(observedAt, "observedAt");
        if (resolveMs < 0) {
            resolveMs = 0;
        }
    }

    /** Compact JSON for {@code detail_json} column. */
    public String detailJson() {
        return "{\"resolve_ms\":" + resolveMs + ",\"outcome\":\"" + outcome.id() + "\",\"state\":\"" + state + "\"}";
    }
}
