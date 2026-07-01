package io.pingui.probe;

import io.pingui.config.HostAddressKind;
import io.pingui.config.HostAddressParser;

/** Trace subprocess target: literal address family and CLI argument. */
public final class TraceTarget {
    private final String traceArgument;
    private final boolean ipv6Literal;

    private TraceTarget(String traceArgument, boolean ipv6Literal) {
        this.traceArgument = traceArgument;
        this.ipv6Literal = ipv6Literal;
    }

    /** Parses a configured host entry for trace argv (literal v6 only, not hostname AAAA). */
    public static TraceTarget forTrace(String input) {
        String normalized = HostAddressParser.normalize(input);
        boolean ipv6 = HostAddressParser.kindOf(normalized) == HostAddressKind.IPV6;
        return new TraceTarget(normalized, ipv6);
    }

    public boolean ipv6Literal() {
        return ipv6Literal;
    }

    public String traceArgument() {
        return traceArgument;
    }
}
