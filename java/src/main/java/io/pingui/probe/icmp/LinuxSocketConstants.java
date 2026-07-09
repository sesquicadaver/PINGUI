package io.pingui.probe.icmp;

/** Linux socket constants for raw ICMP. */
final class LinuxSocketConstants {
    static final int AF_INET = 2;
    static final int AF_INET6 = 10;
    static final int SOCK_RAW = 3;
    static final int IPPROTO_ICMP = 1;
    static final int IPPROTO_ICMPV6 = 58;
    static final int IPPROTO_IP = 0;
    static final int IPPROTO_IPV6 = 41;
    static final int IP_TTL = 2;
    static final int IPV6_UNICAST_HOPS = 16;
    static final int SOL_SOCKET = 1;
    static final int SO_RCVTIMEO = 20;

    private LinuxSocketConstants() {}
}
