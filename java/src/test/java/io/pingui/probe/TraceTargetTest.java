package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraceTargetTest {

    @Test
    void detectsIpv6Literal() {
        TraceTarget target = TraceTarget.forTrace("2001:db8::1");
        assertTrue(target.ipv6Literal());
        assertEquals("2001:db8::1", target.traceArgument());
    }

    @Test
    void ipv4LiteralDoesNotUseIpv6Flag() {
        TraceTarget target = TraceTarget.forTrace("8.8.8.8");
        assertFalse(target.ipv6Literal());
        assertEquals("8.8.8.8", target.traceArgument());
    }

    @Test
    void hostnameDoesNotUseIpv6Flag() {
        TraceTarget target = TraceTarget.forTrace("example.com");
        assertFalse(target.ipv6Literal());
        assertEquals("example.com", target.traceArgument());
    }

    @Test
    void bracketedIpv6Normalized() {
        TraceTarget target = TraceTarget.forTrace("[2001:db8::1]");
        assertTrue(target.ipv6Literal());
        assertEquals("2001:db8::1", target.traceArgument());
    }
}
