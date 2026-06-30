package io.pingui.probe;

import java.util.Locale;

/** Selects the trace command builder for the current OS. */
final class TraceCommandFactory {

    private TraceCommandFactory() {}

    static TraceCommandBuilder forCurrentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new WindowsTracertCommand();
        }
        if (os.contains("mac")) {
            return new MacTracerouteCommand();
        }
        return new LinuxTracerouteCommand();
    }
}
