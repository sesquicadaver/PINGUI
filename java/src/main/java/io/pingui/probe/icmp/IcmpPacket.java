package io.pingui.probe.icmp;

/** ICMP echo request builder and IPv4 reply parsing for Linux raw sockets. */
public final class IcmpPacket {
    public static final int ICMP_ECHO = 8;
    public static final int ICMP_ECHO_REPLY = 0;
    public static final int ICMP_TIME_EXCEEDED = 11;

    private IcmpPacket() {}

    /** Build an ICMP echo request datagram (type 8). */
    public static byte[] buildEchoRequest(int id, int sequence) {
        byte[] packet = new byte[8];
        packet[0] = (byte) ICMP_ECHO;
        packet[1] = 0;
        packet[4] = (byte) ((id >> 8) & 0xff);
        packet[5] = (byte) (id & 0xff);
        packet[6] = (byte) ((sequence >> 8) & 0xff);
        packet[7] = (byte) (sequence & 0xff);
        int checksum = checksum(packet);
        packet[2] = (byte) ((checksum >> 8) & 0xff);
        packet[3] = (byte) (checksum & 0xff);
        return packet;
    }

    /** Parse source IPv4 and whether the reply came from the target. */
    public static ProbeResult parseReply(byte[] buffer, int length, String targetIp, double rttMs) {
        if (length < 20) {
            return null;
        }
        int ipHeaderLength = (buffer[0] & 0x0f) * 4;
        if (ipHeaderLength < 20 || length < ipHeaderLength + 8) {
            return null;
        }
        String sourceIp = formatIpv4(buffer, 12);
        int icmpType = buffer[ipHeaderLength] & 0xff;
        boolean target = targetIp.equals(sourceIp);
        if (icmpType == ICMP_ECHO_REPLY) {
            return new ProbeResult(sourceIp, rttMs, target);
        }
        if (icmpType == ICMP_TIME_EXCEEDED) {
            return new ProbeResult(sourceIp, rttMs, false);
        }
        return null;
    }

    static int checksum(byte[] data) {
        int sum = 0;
        int i = 0;
        while (i + 1 < data.length) {
            sum += ((data[i] & 0xff) << 8) | (data[i + 1] & 0xff);
            i += 2;
        }
        if (i < data.length) {
            sum += (data[i] & 0xff) << 8;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        return ~sum & 0xffff;
    }

    private static String formatIpv4(byte[] buffer, int offset) {
        return String.format(
                "%d.%d.%d.%d",
                buffer[offset] & 0xff,
                buffer[offset + 1] & 0xff,
                buffer[offset + 2] & 0xff,
                buffer[offset + 3] & 0xff);
    }

    static int ipv4ToInt(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4: " + ip);
        }
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        int c = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);
        return (a << 24) | (b << 16) | (c << 8) | d;
    }
}
