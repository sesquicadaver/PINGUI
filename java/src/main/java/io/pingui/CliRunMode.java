package io.pingui;

/** Headless CLI subcommands (P12-010). */
public enum CliRunMode {
    GUI,
    EXPORT,
    TELEMETRY_RETENTION,
    TELEMETRY_DUMP,
    POLL_RETENTION,
    INTEGRITY_CHECK,
    /** One-shot repair of legacy probe-error poll_result tri-state (P34-008). */
    REPAIR_POLL_RESULT,
    DAEMON,
    STOP,
    STATUS
}
