package io.pingui.probe;

/** Outcome class of a TCP connect attempt (P29-005). */
public enum TcpConnectOutcome {
    SUCCESS,
    REFUSED,
    TIMEOUT,
    DNS_ERROR,
    ERROR
}
