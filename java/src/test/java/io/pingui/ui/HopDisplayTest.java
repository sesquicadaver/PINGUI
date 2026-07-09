package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HopDisplayTest {
    @Test
    void ipv4Unchanged() {
        assertEquals("8.8.8.8", HopDisplay.formatHopIp("8.8.8.8"));
    }

    @Test
    void ipv6WrappedInBrackets() {
        assertEquals("[2001:4860:4860::8888]", HopDisplay.formatHopIp("2001:4860:4860::8888"));
        assertEquals("[::1]", HopDisplay.formatHopIp("::1"));
    }

    @Test
    void alreadyBracketedUnchanged() {
        assertEquals("[2001:db8::1]", HopDisplay.formatHopIp("[2001:db8::1]"));
    }

    @Test
    void timeoutUnchanged() {
        assertEquals("*", HopDisplay.formatHopIp("*"));
    }
}
