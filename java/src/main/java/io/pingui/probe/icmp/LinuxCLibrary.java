package io.pingui.probe.icmp;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

/** Minimal libc bindings for Linux raw ICMP sockets. */
interface LinuxCLibrary extends Library {
    LinuxCLibrary INSTANCE = Native.load(Platform.C_LIBRARY_NAME, LinuxCLibrary.class);

    int socket(int domain, int type, int protocol) throws LastErrorException;

    int close(int fd) throws LastErrorException;

    int setsockopt(int socket, int level, int option, Pointer optval, int optlen) throws LastErrorException;

    long sendto(int socket, Pointer message, long length, int flags, Pointer destAddr, int destLen)
            throws LastErrorException;

    long recvfrom(int socket, Pointer buffer, long length, int flags, Pointer srcAddr, Pointer addrlen)
            throws LastErrorException;

    /** sockaddr_in for IPv4 sendto(). */
    final class SockaddrIn extends Structure {
        public short sinFamily = (short) LinuxSocketConstants.AF_INET;
        public short sinPort;
        public int sinAddr;
        public byte[] sinZero = new byte[8];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sinFamily", "sinPort", "sinAddr", "sinZero");
        }
    }

    /** struct timeval for SO_RCVTIMEO. */
    final class TimeVal extends Structure {
        public long tvSec;
        public long tvUsec;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("tvSec", "tvUsec");
        }
    }
}
