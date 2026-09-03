package io.pingui;

/** Headless CLI subcommands (P12-010). */
public enum CliRunMode {
    GUI,
    EXPORT,
    TELEMETRY_RETENTION,
    TELEMETRY_DUMP,
    POLL_RETENTION,
    INTEGRITY_CHECK,
    DAEMON,
    STOP,
    STATUS
}
