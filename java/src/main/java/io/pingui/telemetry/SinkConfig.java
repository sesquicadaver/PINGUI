package io.pingui.telemetry;

import java.util.Objects;
import java.util.Set;

/**
 * Shared remote LOG sink policy (P16-033 / ADR_TELEMETRY / SPIKE_LOG_SINKS).
 *
 * <p>When any syslog / GELF / Loki sink is enabled, operators share one {@code events_only} flag.
 * Default is {@code true} so high-frequency RTT samples never reach LOG unless explicitly opted out.
 * YAML wiring lands in P16-040; this type is the programmatic source of truth until then.
 */
public record SinkConfig(boolean eventsOnly) {
    /** Registry ids for remote LOG sinks that must share {@link #eventsOnly()}. */
    public static final Set<String> REMOTE_LOG_IDS =
            Set.of(SyslogSink.ID, GelfSink.ID, LokiPushSink.ID, OtlpHttpTelemetrySink.ID);

    /** Production-safe default: events only (no high-freq samples on LOG). */
    public static SinkConfig defaults() {
        return new SinkConfig(true);
    }

    /**
     * Policy entry when enabling one or more remote LOG sinks.
     *
     * @param eventsOnly shared flag for syslog + GELF + Loki ({@code true} recommended)
     */
    public static SinkConfig forRemoteLogSinks(boolean eventsOnly) {
        return new SinkConfig(eventsOnly);
    }

    public static boolean isRemoteLogSink(String sinkId) {
        return sinkId != null && REMOTE_LOG_IDS.contains(sinkId);
    }

    /** Null-safe resolve; null config → {@link #defaults()}. */
    public static SinkConfig require(SinkConfig config) {
        return Objects.requireNonNullElse(config, defaults());
    }

    public SinkConfig withEventsOnly(boolean eventsOnly) {
        return new SinkConfig(eventsOnly);
    }
}
