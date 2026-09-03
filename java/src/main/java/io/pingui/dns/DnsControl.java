package io.pingui.dns;

import java.io.InterruptedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Forward DNS control helpers: timed resolve + exception classification (P29-004).
 *
 * <p>Does not raise quality alerts or open incidents — callers persist {@link DnsControlEvent} only.
 */
public final class DnsControl {
    private DnsControl() {}

    /** System resolver: {@link InetAddress#getAllByName(String)}. */
    public static ForwardDnsLookup systemLookup() {
        return InetAddress::getAllByName;
    }

    /**
     * Performs a timed forward lookup and classifies the outcome.
     *
     * @param host normalized hostname
     * @param lookup injectable resolver
     * @param observedAt sample instant
     */
    public static DnsObservation lookup(String host, ForwardDnsLookup lookup, Instant observedAt) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(lookup, "lookup");
        Instant at = observedAt != null ? observedAt : Instant.now();
        long startedNanos = System.nanoTime();
        try {
            InetAddress[] raw = lookup.resolve(host);
            long resolveMs = elapsedMs(startedNanos);
            if (raw == null || raw.length == 0) {
                return new DnsObservation(host, DnsLookupOutcome.NXDOMAIN, List.of(), resolveMs, at);
            }
            return new DnsObservation(host, DnsLookupOutcome.OK, normalizeAddresses(raw), resolveMs, at);
        } catch (Exception ex) {
            long resolveMs = elapsedMs(startedNanos);
            return new DnsObservation(host, classify(ex), List.of(), resolveMs, at);
        }
    }

    /** Maps resolver failures to distinct outcomes (best-effort on JDK {@link InetAddress}). */
    static DnsLookupOutcome classify(Throwable error) {
        if (error == null) {
            return DnsLookupOutcome.ERROR;
        }
        for (Throwable cur = error; cur != null; cur = cur.getCause()) {
            if (cur instanceof SocketTimeoutException || cur instanceof InterruptedIOException) {
                return DnsLookupOutcome.TIMEOUT;
            }
            if (cur instanceof UnknownHostException) {
                return classifyUnknownHost(cur.getMessage());
            }
            String message = cur.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("timed out") || lower.contains("timeout")) {
                    return DnsLookupOutcome.TIMEOUT;
                }
                if (lower.contains("servfail") || lower.contains("server failure")) {
                    return DnsLookupOutcome.SERVFAIL;
                }
                if (lower.contains("nxdomain") || lower.contains("name not found") || lower.contains("unknown host")) {
                    return DnsLookupOutcome.NXDOMAIN;
                }
            }
        }
        return DnsLookupOutcome.ERROR;
    }

    private static DnsLookupOutcome classifyUnknownHost(String message) {
        if (message == null || message.isBlank()) {
            return DnsLookupOutcome.NXDOMAIN;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return DnsLookupOutcome.TIMEOUT;
        }
        if (lower.contains("servfail") || lower.contains("server failure")) {
            return DnsLookupOutcome.SERVFAIL;
        }
        return DnsLookupOutcome.NXDOMAIN;
    }

    static List<String> normalizeAddresses(InetAddress[] addresses) {
        List<String> values = new ArrayList<>(addresses.length);
        for (InetAddress address : addresses) {
            if (address == null) {
                continue;
            }
            if (address instanceof Inet4Address inet4) {
                values.add(inet4.getHostAddress());
            } else if (address instanceof Inet6Address inet6) {
                values.add(stripZone(inet6.getHostAddress()).toLowerCase(Locale.ROOT));
            } else {
                values.add(stripZone(address.getHostAddress()));
            }
        }
        Collections.sort(values);
        // Deduplicate while preserving sort order.
        List<String> unique = new ArrayList<>(values.size());
        String previous = null;
        for (String value : values) {
            if (!value.equals(previous)) {
                unique.add(value);
                previous = value;
            }
        }
        return List.copyOf(unique);
    }

    private static String stripZone(String hostAddress) {
        int zone = hostAddress.indexOf('%');
        return zone >= 0 ? hostAddress.substring(0, zone) : hostAddress;
    }

    private static long elapsedMs(long startedNanos) {
        long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
        return ms < 0 ? 0 : ms;
    }
}
