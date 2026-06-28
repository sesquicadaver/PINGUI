package io.pingui.probe;

import io.pingui.probe.icmp.LinuxJnaIcmpTransport;
import io.pingui.probe.icmp.RawIcmpPermission;

/** Selects the appropriate {@link RouteProbe} implementation. */
public final class RouteProbeFactory {
    private RouteProbeFactory() {}

    public static RouteProbe create(ProbeMode mode) {
        return switch (mode) {
            case PROCESS -> new ProcessRouteProbe();
            case RAW -> new RawIcmpRouteProbe();
            case AUTO -> createAuto();
        };
    }

    private static RouteProbe createAuto() {
        if (LinuxJnaIcmpTransport.isLinux()
                && RawIcmpPermission.isAvailable()) {
            return new RawIcmpRouteProbe();
        }
        return new ProcessRouteProbe();
    }

    /** Human-readable label for logs and status lines. */
    public static String describe(ProbeMode mode) {
        if (mode == ProbeMode.RAW) {
            return "raw-icmp";
        }
        if (mode == ProbeMode.AUTO
                && LinuxJnaIcmpTransport.isLinux()
                && RawIcmpPermission.isAvailable()) {
            return "raw-icmp";
        }
        return "process";
    }
}
