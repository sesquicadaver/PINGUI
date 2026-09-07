package io.pingui.probe;

import io.pingui.geoip.IpLiterals;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the real traceroute target IP independently of hop list (P35-001).
 *
 * <p>Never falls back to the last reachable hop — that mis-labels intermediate routers as the
 * endpoint when the destination is unreachable.
 */
final class TraceTargetIp {
    private TraceTargetIp() {}

    /** Unix: {@code traceroute to host (1.2.3.4), 30 hops max}. */
    private static final Pattern UNIX_HEADER =
            Pattern.compile("^\\s*traceroute to\\s+.+?\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    /** Windows: {@code Tracing route to host [1.2.3.4]} or {@code Tracing route to 1.2.3.4 over ...}. */
    private static final Pattern WINDOWS_BRACKET =
            Pattern.compile("Tracing route to\\s+.+?\\[([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);

    private static final Pattern WINDOWS_LITERAL =
            Pattern.compile("Tracing route to\\s+(\\S+)\\s+over", Pattern.CASE_INSENSITIVE);

    /**
     * Picks the authoritative target IP for a subprocess TRACE.
     *
     * @param targetHost configured host or literal
     * @param outputLines raw traceroute/tracert stdout
     * @return dotted/colon IP, or {@code null} when unknown (incomplete path must not confirm)
     */
    static String resolve(String targetHost, List<String> outputLines) {
        String literal = literalHostAddress(targetHost);
        if (literal != null) {
            return literal;
        }
        String fromHeader = parseFromHeader(outputLines);
        if (fromHeader != null) {
            return fromHeader;
        }
        return resolveHostname(targetHost);
    }

    static String parseFromHeader(List<String> lines) {
        if (lines == null) {
            return null;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Matcher unix = UNIX_HEADER.matcher(line);
            if (unix.find()) {
                String candidate = literalHostAddress(unix.group(1));
                if (candidate != null) {
                    return candidate;
                }
            }
            Matcher winBracket = WINDOWS_BRACKET.matcher(line);
            if (winBracket.find()) {
                String candidate = literalHostAddress(winBracket.group(1));
                if (candidate != null) {
                    return candidate;
                }
            }
            Matcher winLiteral = WINDOWS_LITERAL.matcher(line);
            if (winLiteral.find()) {
                String candidate = literalHostAddress(winLiteral.group(1));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Best-effort forward resolve; failures yield {@code null} (no hop-list fallback). */
    static String resolveHostname(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String stripped = host.strip();
        if (literalHostAddress(stripped) != null) {
            return literalHostAddress(stripped);
        }
        try {
            InetAddress address = InetAddress.getByName(stripped);
            if (address.isAnyLocalAddress()) {
                return null;
            }
            return address.getHostAddress();
        } catch (UnknownHostException | SecurityException ex) {
            return null;
        }
    }

    static String literalHostAddress(String raw) {
        InetAddress address = IpLiterals.parseLiteralOrNull(raw == null ? "" : raw);
        return address == null ? null : address.getHostAddress();
    }

    static String normalizeForCompare(String ip) {
        if (ip == null) {
            return null;
        }
        String literal = literalHostAddress(ip.strip());
        return literal != null ? literal.toLowerCase(Locale.ROOT) : ip.strip().toLowerCase(Locale.ROOT);
    }
}
