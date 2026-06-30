package io.pingui.probe.icmp;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Linux raw ICMP transport via JNA (requires CAP_NET_RAW or root). */
public final class LinuxJnaIcmpTransport implements IcmpProbeTransport {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0x5047);

    private final int socketFd;
    private final int probeId;

    private LinuxJnaIcmpTransport(int socketFd, int probeId) {
        this.socketFd = socketFd;
        this.probeId = probeId;
    }

    /** Open a raw ICMP socket when permitted. */
    public static LinuxJnaIcmpTransport open() throws IOException {
        if (!isLinux()) {
            throw new IOException("Raw ICMP is only supported on Linux");
        }
        try {
            int fd = LinuxCLibrary.INSTANCE.socket(
                    LinuxSocketConstants.AF_INET, LinuxSocketConstants.SOCK_RAW, LinuxSocketConstants.IPPROTO_ICMP);
            return new LinuxJnaIcmpTransport(fd, NEXT_ID.incrementAndGet() & 0xffff);
        } catch (LastErrorException ex) {
            throw new IOException("Cannot open raw ICMP socket (need cap_net_raw?): " + ex.getMessage(), ex);
        }
    }

    public static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    @Override
    public ProbeResult sendProbe(String targetIp, int ttl, double timeoutSeconds) throws IOException {
        setTtl(ttl);
        setReceiveTimeout(timeoutSeconds);
        byte[] request = IcmpPacket.buildEchoRequest(probeId, ttl & 0xffff);
        sendEcho(targetIp, request);
        long startNs = System.nanoTime();
        byte[] reply = receiveReply();
        if (reply == null) {
            return null;
        }
        double rttMs = (System.nanoTime() - startNs) / 1_000_000.0;
        return IcmpPacket.parseReply(reply, reply.length, targetIp, rttMs);
    }

    private void setTtl(int ttl) throws IOException {
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

    private void setReceiveTimeout(double timeoutSeconds) throws IOException {
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

    private void sendEcho(String targetIp, byte[] request) throws IOException {
        LinuxCLibrary.SockaddrIn addr = new LinuxCLibrary.SockaddrIn();
        addr.sinAddr = IcmpPacket.ipv4ToInt(targetIp);
        addr.write();
        try (Memory payload = new Memory(request.length)) {
            payload.write(0, request, 0, request.length);
            long sent =
                    LinuxCLibrary.INSTANCE.sendto(socketFd, payload, request.length, 0, addr.getPointer(), addr.size());
            if (sent != request.length) {
                throw new IOException("ICMP sendto incomplete: " + sent + " bytes");
            }
        } catch (LastErrorException ex) {
            throw new IOException("ICMP sendto failed: " + ex.getMessage(), ex);
        }
    }

    private byte[] receiveReply() throws IOException {
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
        try {
            LinuxCLibrary.INSTANCE.close(socketFd);
        } catch (LastErrorException ignored) {
            // Best effort close.
        }
    }
}
