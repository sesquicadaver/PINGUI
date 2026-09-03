package io.pingui.dns;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;
import io.pingui.config.TcpEndpoint;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-host forward-DNS observer: emits events on first sample, address-set change, or outcome
 * change — never as auto-incidents (P29-004).
 */
public final class DnsControlTracker {
    private final ConcurrentHashMap<String, Snapshot> lastByHost = new ConcurrentHashMap<>();
    private final ForwardDnsLookup lookup;

    public DnsControlTracker() {
        this(DnsControl.systemLookup());
    }

    public DnsControlTracker(ForwardDnsLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    /** Clears remembered address sets / outcomes (tests). */
    public void reset() {
        lastByHost.clear();
    }

    /**
     * Observes forward DNS for hostname targets only; IP literals are skipped.
     *
     * @return event when the sample should be persisted; empty when unchanged
     */
    public Optional<DnsControlEvent> observe(String host) {
        return observe(host, Instant.now());
    }

    public Optional<DnsControlEvent> observe(String host, Instant when) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String sessionKey;
        String lookupHost;
        try {
            if (TcpEndpoint.looksLike(host)) {
                TcpEndpoint endpoint = TcpEndpoint.parse(host);
                sessionKey = endpoint.display();
                lookupHost = endpoint.host();
            } else {
                sessionKey = HostAddressParser.normalize(host);
                lookupHost = sessionKey;
            }
        } catch (ConfigError ex) {
            return Optional.empty();
        }
        if (HostAddressParser.kindOf(lookupHost) != HostAddressKind.HOSTNAME) {
            return Optional.empty();
        }
        Instant at = when != null ? when : Instant.now();
        DnsObservation observation = DnsControl.lookup(lookupHost, lookup, at);
        Snapshot previous = lastByHost.put(sessionKey, new Snapshot(observation.outcome(), observation.addresses()));
        if (previous != null
                && previous.outcome() == observation.outcome()
                && previous.addresses().equals(observation.addresses())) {
            return Optional.empty();
        }
        String state = deriveState(previous, observation);
        List<String> previousAddresses = previous == null ? List.of() : previous.addresses();
        String message = formatMessage(state, observation);
        return Optional.of(new DnsControlEvent(
                sessionKey,
                state,
                message,
                previousAddresses,
                observation.addresses(),
                observation.resolveMs(),
                observation.outcome(),
                observation.observedAt()));
    }

    private static String deriveState(Snapshot previous, DnsObservation observation) {
        if (observation.outcome() != DnsLookupOutcome.OK) {
            return observation.outcome().id();
        }
        if (previous != null && !previous.addresses().equals(observation.addresses())) {
            return "change";
        }
        return DnsLookupOutcome.OK.id();
    }

    private static String formatMessage(String state, DnsObservation observation) {
        long ms = observation.resolveMs();
        return switch (state) {
            case "change" -> "DNS address set changed (" + ms + "ms): " + joinAddresses(observation.addresses());
            case "nxdomain" -> "NXDOMAIN (" + ms + "ms)";
            case "timeout" -> "DNS timeout (" + ms + "ms)";
            case "servfail" -> "SERVFAIL (" + ms + "ms)";
            case "error" -> "DNS error (" + ms + "ms)";
            default -> "resolved "
                    + observation.addresses().size()
                    + " address(es) in "
                    + ms
                    + "ms: "
                    + joinAddresses(observation.addresses());
        };
    }

    private static String joinAddresses(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return "—";
        }
        return String.join(", ", addresses);
    }

    private record Snapshot(DnsLookupOutcome outcome, List<String> addresses) {
        Snapshot {
            Objects.requireNonNull(outcome, "outcome");
            addresses = addresses == null ? List.of() : List.copyOf(addresses);
        }
    }
}
