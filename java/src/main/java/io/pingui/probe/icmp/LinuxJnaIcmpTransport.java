package io.pingui.probe.icmp;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Linux raw ICMP transport via JNA (requires CAP_NET_RAW or root). Supports IPv4 and IPv6. */
public final class LinuxJnaIcmpTransport implements IcmpProbeTransport {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0x5047);

    private Integer v4SocketFd;
    private Integer v6SocketFd;
    private final int probeId;

    private LinuxJnaIcmpTransport(int probeId) {
        this.probeId = probeId;
    }

    /** Open a raw ICMP transport when permitted (probes v4 socket for cap check). */
    public static LinuxJnaIcmpTransport open() throws IOException {
        if (!isLinux()) {
            throw new IOException("Raw ICMP is only supported on Linux");
        }
        LinuxJnaIcmpTransport transport = new LinuxJnaIcmpTransport(NEXT_ID.incrementAndGet() & 0xffff);
        transport.ensureV4Socket();
        return transport;
    }

    public static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    @Override
    public ProbeResult sendProbe(String targetIp, int ttl, double timeoutSeconds) throws IOException {
        if (IcmpV6Packet.isIpv6Literal(targetIp)) {
            return sendProbeV6(targetIp, ttl, timeoutSeconds);
        }
        return sendProbeV4(targetIp, ttl, timeoutSeconds);
    }

    private ProbeResult sendProbeV4(String targetIp, int ttl, double timeoutSeconds) throws IOException {
        int socketFd = ensureV4Socket();
        setTtl(socketFd, ttl);
        setReceiveTimeout(socketFd, timeoutSeconds);
        byte[] request = IcmpPacket.buildEchoRequest(probeId, ttl & 0xffff);
        sendEchoV4(socketFd, targetIp, request);
        long startNs = System.nanoTime();
        byte[] reply = receiveReply(socketFd);
        if (reply == null) {
            return null;
        }
        double rttMs = (System.nanoTime() - startNs) / 1_000_000.0;
        return IcmpPacket.parseReply(reply, reply.length, targetIp, rttMs);
    }

    private ProbeResult sendProbeV6(String targetIp, int hopLimit, double timeoutSeconds) throws IOException {
        int socketFd = ensureV6Socket();
        setHopLimit(socketFd, hopLimit);
        setReceiveTimeout(socketFd, timeoutSeconds);
        byte[] request = IcmpV6Packet.buildEchoRequest(probeId, hopLimit & 0xffff);
        sendEchoV6(socketFd, targetIp, request);
        long startNs = System.nanoTime();
        byte[] reply = receiveReply(socketFd);
        if (reply == null) {
            return null;
        }
        double rttMs = (System.nanoTime() - startNs) / 1_000_000.0;
        return IcmpV6Packet.parseReply(reply, reply.length, targetIp, rttMs);
    }

    private int ensureV4Socket() throws IOException {
        if (v4SocketFd != null) {
            return v4SocketFd;
        }
        try {
            v4SocketFd = LinuxCLibrary.INSTANCE.socket(
                    LinuxSocketConstants.AF_INET, LinuxSocketConstants.SOCK_RAW, LinuxSocketConstants.IPPROTO_ICMP);
            return v4SocketFd;
        } catch (LastErrorException ex) {
            throw new IOException("Cannot open raw ICMP socket (need cap_net_raw?): " + ex.getMessage(), ex);
        }
    }

    private int ensureV6Socket() throws IOException {
        if (v6SocketFd != null) {
            return v6SocketFd;
        }
        try {
            v6SocketFd = LinuxCLibrary.INSTANCE.socket(
                    LinuxSocketConstants.AF_INET6, LinuxSocketConstants.SOCK_RAW, LinuxSocketConstants.IPPROTO_ICMPV6);
            return v6SocketFd;
        } catch (LastErrorException ex) {
            throw new IOException("Cannot open raw ICMPv6 socket (need cap_net_raw?): " + ex.getMessage(), ex);
        }
    }

    private void setTtl(int socketFd, int ttl) throws IOException {
        try (Memory ttlMem = new Memory(4)) {
            ttlMem.setInt(0, ttl);
            int rc = LinuxCLibrary.INSTANCE.setsockopt(
                    socketFd, LinuxSocketConstants.IPPROTO_IP, LinuxSocketConstants.IP_TTL, ttlMem, 4);
            if (rc != 0) {
                throw new IOException("setsockopt IP_TTL failed for ttl=" + ttl);
            }
        } catch (LastErrorException ex) {
            throw new IOException("setsockopt IP_TTL failed: " + ex.getMessage(), ex);
        }
    }

    private void setHopLimit(int socketFd, int hopLimit) throws IOException {
        try (Memory hopMem = new Memory(4)) {
            hopMem.setInt(0, hopLimit);
            int rc = LinuxCLibrary.INSTANCE.setsockopt(
                    socketFd, LinuxSocketConstants.IPPROTO_IPV6, LinuxSocketConstants.IPV6_UNICAST_HOPS, hopMem, 4);
            if (rc != 0) {
                throw new IOException("setsockopt IPV6_UNICAST_HOPS failed for hopLimit=" + hopLimit);
            }
        } catch (LastErrorException ex) {
            throw new IOException("setsockopt IPV6_UNICAST_HOPS failed: " + ex.getMessage(), ex);
        }
    }

    private void setReceiveTimeout(int socketFd, double timeoutSeconds) throws IOException {
        long sec = (long) timeoutSeconds;
        long usec = (long) ((timeoutSeconds - sec) * 1_000_000);
        LinuxCLibrary.TimeVal tv = new LinuxCLibrary.TimeVal();
        tv.tvSec = sec;
        tv.tvUsec = usec;
        tv.write();
        try {
            int rc = LinuxCLibrary.INSTANCE.setsockopt(
                    socketFd,
                    LinuxSocketConstants.SOL_SOCKET,
                    LinuxSocketConstants.SO_RCVTIMEO,
                    tv.getPointer(),
                    tv.size());
            if (rc != 0) {
                throw new IOException("setsockopt SO_RCVTIMEO failed");
            }
        } catch (LastErrorException ex) {
            throw new IOException("setsockopt SO_RCVTIMEO failed: " + ex.getMessage(), ex);
        }
    }

    private void sendEchoV4(int socketFd, String targetIp, byte[] request) throws IOException {
        LinuxCLibrary.SockaddrIn addr = new LinuxCLibrary.SockaddrIn();
        addr.sinAddr = IcmpPacket.ipv4ToInt(targetIp);
        addr.write();
        sendPayload(socketFd, request, addr.getPointer(), addr.size());
    }

    private void sendEchoV6(int socketFd, String targetIp, byte[] request) throws IOException {
        LinuxCLibrary.SockaddrIn6 addr = new LinuxCLibrary.SockaddrIn6();
        addr.sin6Addr = IcmpV6Packet.ipv6ToBytes(targetIp);
        addr.write();
        sendPayload(socketFd, request, addr.getPointer(), addr.size());
    }

    private void sendPayload(int socketFd, byte[] request, Pointer destAddr, int destLen) throws IOException {
        try (Memory payload = new Memory(request.length)) {
            payload.write(0, request, 0, request.length);
            long sent = LinuxCLibrary.INSTANCE.sendto(socketFd, payload, request.length, 0, destAddr, destLen);
            if (sent != request.length) {
                throw new IOException("ICMP sendto incomplete: " + sent + " bytes");
            }
        } catch (LastErrorException ex) {
            throw new IOException("ICMP sendto failed: " + ex.getMessage(), ex);
        }
    }

    private byte[] receiveReply(int socketFd) throws IOException {
        int bufferSize = 512;
        try (Memory buffer = new Memory(bufferSize)) {
            try {
                long received =
                        LinuxCLibrary.INSTANCE.recvfrom(socketFd, buffer, bufferSize, 0, Pointer.NULL, Pointer.NULL);
                if (received <= 0) {
                    return null;
                }
                byte[] data = new byte[(int) received];
                buffer.read(0, data, 0, (int) received);
                return data;
            } catch (LastErrorException ex) {
                if (isTimeoutError(ex)) {
                    return null;
                }
                throw new IOException("ICMP recvfrom failed: " + ex.getMessage(), ex);
            }
        }
    }

    private static boolean isTimeoutError(LastErrorException ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("EAGAIN") || message.contains("EWOULDBLOCK"));
    }

    @Override
    public void close() {
        closeFd(v4SocketFd);
        closeFd(v6SocketFd);
        v4SocketFd = null;
        v6SocketFd = null;
    }

    private static void closeFd(Integer fd) {
        if (fd == null) {
            return;
        }
        try {
            LinuxCLibrary.INSTANCE.close(fd);
        } catch (LastErrorException ignored) {
            // Best effort close.
        }
    }
}
