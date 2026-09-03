package io.pingui.probe;

import java.util.Objects;

/**
 * Timed DNS + TCP connect sample for {@code host:port} (P29-005).
 *
 * @param target display form ({@code host:port})
 * @param resolvedIp chosen A/AAAA address, or empty on DNS failure
 * @param dnsMs DNS resolve wall time
 * @param connectMs TCP connect wall time (0 when connect not attempted)
 * @param outcome success / refused / timeout / dns / other
 * @param message human detail
 */
public record TcpConnectResult(
        String target, String resolvedIp, long dnsMs, long connectMs, TcpConnectOutcome outcome, String message) {

    public TcpConnectResult {
        Objects.requireNonNull(target, "target");
        resolvedIp = resolvedIp == null ? "" : resolvedIp;
        Objects.requireNonNull(outcome, "outcome");
        message = message == null ? "" : message;
        if (dnsMs < 0) {
            dnsMs = 0;
        }
        if (connectMs < 0) {
            connectMs = 0;
        }
    }

    public boolean success() {
        return outcome == TcpConnectOutcome.SUCCESS;
    }
}
