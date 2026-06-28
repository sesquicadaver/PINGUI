package io.pingui.probe.icmp;

import java.io.IOException;

/** Checks whether raw ICMP sockets can be opened on Linux. */
public final class RawIcmpPermission {
    private RawIcmpPermission() {}

    public static boolean isAvailable() {
        if (!LinuxJnaIcmpTransport.isLinux()) {
            return false;
        }
        try (LinuxJnaIcmpTransport transport = LinuxJnaIcmpTransport.open()) {
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
