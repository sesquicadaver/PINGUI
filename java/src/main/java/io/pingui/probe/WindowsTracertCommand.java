package io.pingui.probe;

import java.nio.file.Files;
import java.util.List;

/** Windows {@code tracert} argv ({@code -d}, {@code -w} ≥ 4000 ms). */
final class WindowsTracertCommand implements TraceCommandBuilder {

    @Override
    public List<String> buildCommand(String targetHost, int maxHops, double timeoutSeconds) {
        TraceTarget target = TraceTarget.forTrace(targetHost);
        int waitMs = TraceProcessTiming.windowsTracertWaitMs(timeoutSeconds);
        String tracert = TracerouteExecutables.resolveTracertExecutable(Files::isExecutable);
        return TraceCommandSupport.finishCommand(
                List.of(tracert, "-d", "-h", String.valueOf(maxHops), "-w", String.valueOf(waitMs)), target);
    }
}
