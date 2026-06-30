package io.pingui.probe;

import java.util.List;

/** Linux traceroute argv (GNU inetutils or BSD-style flags). */
final class LinuxTracerouteCommand implements TraceCommandBuilder {

    private final TracerouteFlavor flavor;

    LinuxTracerouteCommand(TracerouteFlavor flavor) {
        this.flavor = flavor;
    }

    LinuxTracerouteCommand() {
        this(TracerouteFlavorDetector.resolveFlavor());
    }

    @Override
    public List<String> buildCommand(String targetHost, int maxHops, double timeoutSeconds) {
        String traceroute = TracerouteExecutables.resolveTracerouteExecutable();
        int waitSec = Math.max(1, (int) Math.ceil(timeoutSeconds));
        if (flavor == TracerouteFlavor.GNU_INETUTILS) {
            return List.of(
                    traceroute, "-m", String.valueOf(maxHops), "-w", String.valueOf(waitSec), "-q", "1", targetHost);
        }
        return List.of(
                traceroute, "-n", "-w", String.valueOf(waitSec), "-m", String.valueOf(maxHops), "-q", "1", targetHost);
    }
}
