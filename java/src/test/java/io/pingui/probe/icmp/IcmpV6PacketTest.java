package io.pingui.probe.icmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IcmpV6PacketTest {
    @Test
    void buildEchoRequestHasType128AndZeroChecksum() {
        byte[] packet = IcmpV6Packet.buildEchoRequest(0x5047, 2);
        assertEquals(IcmpV6Packet.ICMP6_ECHO_REQUEST, packet[0] & 0xff);
        assertEquals(0, packet[2] & 0xff);
        assertEquals(0, packet[3] & 0xff);
        assertEquals(0x50, packet[4] & 0xff);
        assertEquals(0x47, packet[5] & 0xff);
    }

    @Test
    void parseTimeExceededReply() {
        byte[] buffer = new byte[48];
        writeIpv6Source(buffer, 8, 0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        buffer[40] = (byte) IcmpV6Packet.ICMP6_TIME_EXCEEDED;
        ProbeResult result = IcmpV6Packet.parseReply(buffer, buffer.length, "2001:db8::2", 9.0);
        assertNotNull(result);
        assertEquals("2001:db8::1", result.sourceIp());
        assertFalse(result.target());
        assertEquals(9.0, result.rttMs());
    }

    @Test
    void parseEchoReplyFromTarget() {
        byte[] buffer = new byte[48];
        writeIpv6Source(buffer, 8, 0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2);
        buffer[40] = (byte) IcmpV6Packet.ICMP6_ECHO_REPLY;
        ProbeResult result = IcmpV6Packet.parseReply(buffer, buffer.length, "2001:db8::2", 4.2);
        assertNotNull(result);
        assertEquals("2001:db8::2", result.sourceIp());
        assertTrue(result.target());
    }

    @Test
    void parseReplyRejectsShortBuffer() {
        assertNull(IcmpV6Packet.parseReply(new byte[20], 20, "2001:db8::1", 1.0));
    }

    @Test
    void ipv6ToBytesRoundTrip() {
        byte[] bytes = IcmpV6Packet.ipv6ToBytes("2001:db8::1");
        assertEquals(16, bytes.length);
        assertEquals(0x20, bytes[0] & 0xff);
        assertEquals(0x01, bytes[1] & 0xff);
    }

    @Test
    void isIpv6LiteralDetectsColon() {
        assertTrue(IcmpV6Packet.isIpv6Literal("2001:db8::1"));
        assertFalse(IcmpV6Packet.isIpv6Literal("8.8.8.8"));
    }

    private static void writeIpv6Source(byte[] buffer, int offset, int... octets) {
        for (int i = 0; i < octets.length; i++) {
            buffer[offset + i] = (byte) octets[i];
        }
    }
}
