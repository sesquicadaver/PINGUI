package io.pingui.config;

import java.util.Locale;
import java.util.Objects;

/**
 * Parsed {@code host:port} / {@code [ipv6]:port} target for TCP connect probes (P29-005).
 *
 * @param host normalized hostname or IP literal (no brackets)
 * @param port TCP port 1–65535
 */
public record TcpEndpoint(String host, int port) {
    public TcpEndpoint {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new ConfigError("TCP endpoint host required");
        }
        if (port < 1 || port > 65535) {
            throw new ConfigError("TCP port must be 1–65535, got " + port);
        }
        host = host.strip();
    }

    /** True when {@code entry} uses explicit port syntax (IPv6 must be bracketed). */
    public static boolean looksLike(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        String trimmed = entry.strip();
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            return close > 1 && close + 1 < trimmed.length() && trimmed.charAt(close + 1) == ':';
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            return false;
        }
        // Ambiguous bare IPv6 — require brackets when a port is present.
        if (trimmed.indexOf(':') != colon) {
            return false;
        }
        return isPortToken(trimmed.substring(colon + 1));
    }

    /** Parses and validates a TCP endpoint; normalizes host via {@link HostAddressParser}. */
    public static TcpEndpoint parse(String entry) {
        if (entry == null || entry.isBlank()) {
            throw new ConfigError("Invalid TCP endpoint: '" + entry + "'");
        }
        String trimmed = entry.strip();
        String hostPart;
        String portPart;
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            if (close < 2 || close + 1 >= trimmed.length() || trimmed.charAt(close + 1) != ':') {
                throw new ConfigError("Invalid TCP endpoint (expected [ipv6]:port): '" + entry + "'");
            }
            hostPart = trimmed.substring(1, close);
            portPart = trimmed.substring(close + 2);
        } else {
            int colon = trimmed.lastIndexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                throw new ConfigError("Invalid TCP endpoint (expected host:port): '" + entry + "'");
            }
            if (trimmed.indexOf(':') != colon) {
                throw new ConfigError("IPv6 TCP targets must use [address]:port form: '" + entry + "'");
            }
            hostPart = trimmed.substring(0, colon);
            portPart = trimmed.substring(colon + 1);
        }
        if (!isPortToken(portPart)) {
            throw new ConfigError("Invalid TCP port in '" + entry + "'");
        }
        int port = Integer.parseInt(portPart);
        String normalizedHost = HostAddressParser.normalize(hostPart);
        if (HostAddressParser.kindOf(normalizedHost) == HostAddressKind.HOSTNAME) {
            normalizedHost = normalizedHost.toLowerCase(Locale.ROOT);
        }
        return new TcpEndpoint(normalizedHost, port);
    }

    /** Canonical display form used as session/YAML address. */
    public String display() {
        if (HostAddressParser.kindOf(host) == HostAddressKind.IPV6) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    /** Case-insensitive duplicate key (host + port). */
    public String duplicateKey() {
        return HostAddressParser.duplicateKey(host) + ":" + port;
    }

    private static boolean isPortToken(String token) {
        if (token == null || token.isBlank() || token.length() > 5) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        try {
            int value = Integer.parseInt(token);
            return value >= 1 && value <= 65535;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public String toString() {
        return display();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TcpEndpoint that)) {
            return false;
        }
        return port == that.port && duplicateKey().equals(that.duplicateKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(duplicateKey().toLowerCase(Locale.ROOT), port);
    }
}
