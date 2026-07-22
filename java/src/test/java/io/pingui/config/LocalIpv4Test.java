package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalIpv4Test {
    @Test
    void prefersRfc1918OverPublic() throws Exception {
        List<InetAddress> addresses = List.of(addr("8.8.8.8"), addr("192.168.1.10"), addr("1.1.1.1"));
        assertEquals("192.168.1.10", LocalIpv4.resolveOperatorLan(() -> addresses));
    }

    @Test
    void fallsBackToNonLoopbackThenUnknown() throws Exception {
        List<InetAddress> publicOnly = List.of(addr("203.0.113.5"));
        assertEquals("203.0.113.5", LocalIpv4.resolveOperatorLan(() -> publicOnly));
        assertEquals(LocalIpv4.FALLBACK, LocalIpv4.resolveOperatorLan(List::of));
    }

    @Test
    void sanitizeReplacesDots() {
        assertEquals("192-168-1-10", LocalIpv4.sanitizeForFilename("192.168.1.10"));
        assertEquals(LocalIpv4.FALLBACK, LocalIpv4.sanitizeForFilename("  "));
    }

    @Test
    void rfc1918Detection() {
        assertTrue(LocalIpv4.isRfc1918("10.0.0.1"));
        assertTrue(LocalIpv4.isRfc1918("172.16.0.1"));
        assertTrue(LocalIpv4.isRfc1918("172.31.255.255"));
        assertTrue(LocalIpv4.isRfc1918("192.168.0.1"));
        assertFalse(LocalIpv4.isRfc1918("172.15.0.1"));
        assertFalse(LocalIpv4.isRfc1918("8.8.8.8"));
    }

    private static InetAddress addr(String host) throws UnknownHostException {
        return InetAddress.getByName(host);
    }
}
