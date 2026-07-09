package io.pingui.probe.icmp;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressParser;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/** ICMPv6 echo request builder and IPv6 reply parsing for Linux raw sockets. */
public final class IcmpV6Packet {
    public static final int ICMP6_ECHO_REQUEST = 128;
    public static final int ICMP6_ECHO_REPLY = 129;
    public static final int ICMP6_TIME_EXCEEDED = 3;
    static final int IPV6_HEADER_LENGTH = 40;

    private IcmpV6Packet() {}

    /** Build an ICMPv6 echo request (type 128). Checksum 0 lets the Linux kernel compute it. */
    public static byte[] buildEchoRequest(int id, int sequence) {
        byte[] packet = new byte[8];
        packet[0] = (byte) ICMP6_ECHO_REQUEST;
        packet[1] = 0;
        packet[2] = 0;
        packet[3] = 0;
        packet[4] = (byte) ((id >> 8) & 0xff);
        packet[5] = (byte) (id & 0xff);
        packet[6] = (byte) ((sequence >> 8) & 0xff);
        packet[7] = (byte) (sequence & 0xff);
        return packet;
    }

    /** Parse source IPv6 and whether the reply came from the target. */
    public static ProbeResult parseReply(byte[] buffer, int length, String targetIp, double rttMs) {
        if (length < IPV6_HEADER_LENGTH + 8) {
            return null;
        }
        String sourceIp = formatIpv6(buffer, 8);
        int icmpType = buffer[IPV6_HEADER_LENGTH] & 0xff;
        boolean target = sameAddress(targetIp, sourceIp);
        if (icmpType == ICMP6_ECHO_REPLY) {
            return new ProbeResult(sourceIp, rttMs, target);
        }
        if (icmpType == ICMP6_TIME_EXCEEDED) {
            return new ProbeResult(sourceIp, rttMs, false);
        }
        return null;
    }

    public static boolean isIpv6Literal(String address) {
        return address != null && address.indexOf(':') >= 0;
    }

    public static byte[] ipv6ToBytes(String ip) {
        try {
            InetAddress parsed = InetAddress.getByName(stripBrackets(ip));
            byte[] bytes = parsed.getAddress();
            if (bytes.length != 16) {
                throw new IllegalArgumentException("Not an IPv6 address: " + ip);
            }
            return bytes;
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid IPv6: " + ip, ex);
        }
    }

    private static String formatIpv6(byte[] buffer, int offset) {
        byte[] addr = Arrays.copyOfRange(buffer, offset, offset + 16);
        try {
            String host = InetAddress.getByAddress(addr).getHostAddress();
            return HostAddressParser.normalize(host);
        } catch (UnknownHostException | ConfigError ex) {
            return "?";
        }
    }

    private static boolean sameAddress(String left, String right) {
        try {
            return HostAddressParser.normalize(left).equals(HostAddressParser.normalize(right));
        } catch (ConfigError ex) {
            return left.equalsIgnoreCase(right);
        }
    }

    private static String stripBrackets(String value) {
        if (value.startsWith("[") && value.endsWith("]") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
