package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProbeOutcomeTest {
    @Test
    void mapsTcpOutcomes() {
        assertEquals(ProbeOutcome.SUCCESS, ProbeOutcome.fromTcp(TcpConnectOutcome.SUCCESS));
        assertEquals(ProbeOutcome.REFUSED, ProbeOutcome.fromTcp(TcpConnectOutcome.REFUSED));
        assertEquals(ProbeOutcome.TIMEOUT, ProbeOutcome.fromTcp(TcpConnectOutcome.TIMEOUT));
        assertEquals(ProbeOutcome.DNS_ERROR, ProbeOutcome.fromTcp(TcpConnectOutcome.DNS_ERROR));
        assertEquals(ProbeOutcome.NETWORK_ERROR, ProbeOutcome.fromTcp(TcpConnectOutcome.ERROR));
    }

    @Test
    void wireRoundTrip() {
        for (ProbeOutcome outcome : ProbeOutcome.values()) {
            assertEquals(outcome, ProbeOutcome.fromWire(outcome.wire()));
        }
    }
}
