package io.pingui.probe.icmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

class LinuxCLibraryTest {
    @Test
    void sockaddrIn6LayoutOnLinux() {
        assumeTrue(Platform.isLinux());
        LinuxCLibrary.SockaddrIn6 addr = new LinuxCLibrary.SockaddrIn6();
        assertEquals((short) LinuxSocketConstants.AF_INET6, addr.sin6Family);
        assertEquals(28, addr.size());
    }

    @Test
    void socketConstantsMatchLinuxHeaders() {
        assertEquals(10, LinuxSocketConstants.AF_INET6);
        assertEquals(58, LinuxSocketConstants.IPPROTO_ICMPV6);
        assertEquals(16, LinuxSocketConstants.IPV6_UNICAST_HOPS);
    }
}
