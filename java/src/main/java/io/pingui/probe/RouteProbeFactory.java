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
        RouteProbe process = new ProcessRouteProbe();
        if (LinuxJnaIcmpTransport.isLinux() && RawIcmpPermission.isAvailable()) {
            return new DualStackRouteProbe(new RawIcmpRouteProbe(), process);
        }
        return process;
    }

    /** Human-readable label for logs and status lines. */
    public static String describe(ProbeMode mode) {
        if (mode == ProbeMode.RAW) {
            return "raw-icmp";
        }
        if (mode == ProbeMode.PROCESS) {
            return "process";
        }
        if (mode == ProbeMode.AUTO && LinuxJnaIcmpTransport.isLinux() && RawIcmpPermission.isAvailable()) {
            return "auto (v4 raw, v6 process)";
        }
        return "process";
    }
}
