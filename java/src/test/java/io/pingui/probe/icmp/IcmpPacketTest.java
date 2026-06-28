package io.pingui.probe.icmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IcmpPacketTest {
    @Test
    void buildEchoRequestHasValidChecksum() {
        byte[] packet = IcmpPacket.buildEchoRequest(0x5047, 3);
        assertEquals(IcmpPacket.ICMP_ECHO, packet[0] & 0xff);
        int checksum = ((packet[2] & 0xff) << 8) | (packet[3] & 0xff);
        byte[] copy = packet.clone();
        copy[2] = 0;
        copy[3] = 0;
        assertEquals(checksum, IcmpPacket.checksum(copy));
    }

    @Test
    void parseTimeExceededReply() {
        byte[] buffer = new byte[28];
        buffer[0] = 0x45;
        buffer[12] = 10;
        buffer[13] = 0;
        buffer[14] = 0;
        buffer[15] = 1;
        buffer[20] = (byte) IcmpPacket.ICMP_TIME_EXCEEDED;
        ProbeResult result = IcmpPacket.parseReply(buffer, buffer.length, "8.8.8.8", 12.5);
        assertNotNull(result);
        assertEquals("10.0.0.1", result.sourceIp());
        assertFalse(result.target());
        assertEquals(12.5, result.rttMs());
    }

    @Test
    void parseEchoReplyFromTarget() {
        byte[] buffer = new byte[28];
        buffer[0] = 0x45;
        buffer[12] = 8;
        buffer[13] = 8;
        buffer[14] = 8;
        buffer[15] = 8;
        buffer[20] = (byte) IcmpPacket.ICMP_ECHO_REPLY;
        ProbeResult result = IcmpPacket.parseReply(buffer, buffer.length, "8.8.8.8", 4.2);
        assertNotNull(result);
        assertEquals("8.8.8.8", result.sourceIp());
        assertTrue(result.target());
    }
}
