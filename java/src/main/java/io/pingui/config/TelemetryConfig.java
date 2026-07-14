package io.pingui.config;

import io.pingui.telemetry.GelfSink;
import io.pingui.telemetry.SinkConfig;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-profile telemetry YAML ({@code telemetry:}) — P16-040 / ADR_TELEMETRY.
 *
 * <p>Default: all local/remote sinks off, {@code events_only=true}, {@code log_aggregates=false}.
 * Presence of {@code sqlite}/{@code jsonl_dir}/syslog/gelf/loki entries enables that sink in the
 * config model. CLI overrides: P16-041; secret redaction: P16-042; daemon sink assembly follows.
 */
public record TelemetryConfig(
        boolean eventsOnly,
        boolean logAggregates,
        Optional<Path> sqlitePath,
        Optional<Path> jsonlDir,
        Optional<SyslogSinkConfig> syslog,
        Optional<GelfSinkConfig> gelf,
        Optional<LokiSinkConfig> loki) {

    public TelemetryConfig {
        sqlitePath = sqlitePath != null ? sqlitePath : Optional.empty();
        jsonlDir = jsonlDir != null ? jsonlDir : Optional.empty();
        syslog = syslog != null ? syslog : Optional.empty();
        gelf = gelf != null ? gelf : Optional.empty();
        loki = loki != null ? loki : Optional.empty();
    }

    public static TelemetryConfig defaults() {
        return new TelemetryConfig(
                true, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public SinkConfig toSinkConfig() {
        return SinkConfig.forRemoteLogSinks(eventsOnly);
    }

    public boolean isDefault() {
        return eventsOnly
                && !logAggregates
                && sqlitePath.isEmpty()
                && jsonlDir.isEmpty()
                && syslog.isEmpty()
                && gelf.isEmpty()
                && loki.isEmpty();
    }

    public TelemetryConfig withEventsOnly(boolean eventsOnly) {
        return new TelemetryConfig(eventsOnly, logAggregates, sqlitePath, jsonlDir, syslog, gelf, loki);
    }

    public TelemetryConfig withLogAggregates(boolean logAggregates) {
        return new TelemetryConfig(eventsOnly, logAggregates, sqlitePath, jsonlDir, syslog, gelf, loki);
    }

    /** Syslog remote endpoint from YAML {@code telemetry.syslog}. */
    public record SyslogSinkConfig(String host, int port, boolean tls) {
        public SyslogSinkConfig {
            host = requireHost(host);
            requirePort(port);
        }
    }

    /** GELF remote endpoint from YAML {@code telemetry.gelf}. */
    public record GelfSinkConfig(String host, int port, GelfSink.Transport transport) {
        public GelfSinkConfig {
            host = requireHost(host);
            requirePort(port);
            transport = transport != null ? transport : GelfSink.Transport.TCP;
        }

        public static GelfSink.Transport parseTransport(String raw) {
            if (raw == null || raw.isBlank()) {
                return GelfSink.Transport.TCP;
            }
            return switch (raw.strip().toLowerCase(Locale.ROOT)) {
                case "tcp" -> GelfSink.Transport.TCP;
                case "udp" -> GelfSink.Transport.UDP;
                default -> throw new ConfigError("telemetry.gelf.transport must be tcp or udp");
            };
        }
    }

    /** Loki remote endpoint from YAML {@code telemetry.loki}. */
    public record LokiSinkConfig(String url, String site) {
        public LokiSinkConfig {
            url = requireNonBlank(url, "telemetry.loki.url");
            site = requireNonBlank(site, "telemetry.loki.site");
        }
    }

    private static String requireHost(String host) {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new ConfigError("telemetry sink host must be non-blank");
        }
        return host.strip();
    }

    private static void requirePort(int port) {
        if (port < 1 || port > 65535) {
            throw new ConfigError("telemetry sink port must be 1..65535");
        }
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new ConfigError(field + " must be non-blank");
        }
        return value.strip();
    }
}
