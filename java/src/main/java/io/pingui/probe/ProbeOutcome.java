package io.pingui.probe;

/**
 * Structured finished-poll outcome for {@code poll_result} (P32-003).
 *
 * <p>Maps TCP connect classes and ICMP/trace reachability without inventing packet loss.
 */
public enum ProbeOutcome {
    SUCCESS,
    TIMEOUT,
    REFUSED,
    DNS_ERROR,
    NETWORK_ERROR;

    /** Wire / SQLite value (stable). */
    public String wire() {
        return name();
    }

    public static ProbeOutcome fromWire(String value) {
        if (value == null || value.isBlank()) {
            return NETWORK_ERROR;
        }
        try {
            return ProbeOutcome.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NETWORK_ERROR;
        }
    }

    /** Maps {@link TcpConnectOutcome} onto the shared poll_result vocabulary. */
    public static ProbeOutcome fromTcp(TcpConnectOutcome tcp) {
        if (tcp == null) {
            return NETWORK_ERROR;
        }
        return switch (tcp) {
            case SUCCESS -> SUCCESS;
            case TIMEOUT -> TIMEOUT;
            case REFUSED -> REFUSED;
            case DNS_ERROR -> DNS_ERROR;
            case ERROR -> NETWORK_ERROR;
        };
    }
}
