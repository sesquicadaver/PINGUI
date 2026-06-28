package io.pingui.platform;

import io.pingui.probe.icmp.LinuxJnaIcmpTransport;

/** OS-specific feature gates for the Java edition. */
public final class PlatformCapabilities {
    private PlatformCapabilities() {}

    /** Expert ping uses Linux iputils {@code ping(8)} flags; unavailable elsewhere. */
    public static boolean expertPingSupported() {
        return LinuxJnaIcmpTransport.isLinux();
    }
}
