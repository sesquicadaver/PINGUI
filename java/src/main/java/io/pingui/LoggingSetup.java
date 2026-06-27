package io.pingui;

/** Configure SLF4J simple logger levels for GUI vs verbose CLI. */
public final class LoggingSetup {
    private LoggingSetup() {}

    public static void configure(boolean verbose) {
        System.setProperty(
                "org.slf4j.simpleLogger.defaultLogLevel", verbose ? "debug" : "error");
        System.setProperty("org.slf4j.simpleLogger.log.io.pingui", verbose ? "debug" : "info");
    }
}
