package io.pingui.probe;

import java.util.List;

/** macOS traceroute argv (BSD dialect, absolute {@code /usr/sbin/traceroute} when present). */
final class MacTracerouteCommand implements TraceCommandBuilder {

    @Override
    public List<String> buildCommand(String targetHost, int maxHops, double timeoutSeconds) {
        String traceroute = TracerouteExecutables.resolveTracerouteExecutable();
        int waitSec = Math.max(1, (int) Math.ceil(timeoutSeconds));
        return List.of(
                traceroute, "-n", "-w", String.valueOf(waitSec), "-m", String.valueOf(maxHops), "-q", "1", targetHost);
    }
}
