package io.pingui.probe;

import java.util.ArrayList;
import java.util.List;

/** Shared trace argv helpers for dual-stack literals. */
final class TraceCommandSupport {

    private TraceCommandSupport() {}

    static List<String> finishCommand(List<String> prefix, TraceTarget target) {
        List<String> command = new ArrayList<>(prefix);
        if (target.ipv6Literal()) {
            command.add("-6");
        }
        command.add(target.traceArgument());
        return List.copyOf(command);
    }
}
