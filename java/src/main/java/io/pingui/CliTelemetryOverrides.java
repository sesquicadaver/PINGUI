package io.pingui;

import io.pingui.config.TelemetryConfig;
import java.nio.file.Path;
import java.util.Optional;

/**
 * CLI overrides for telemetry sinks (P16-041 / P16-080). Empty fields keep YAML {@code telemetry:}
 * values.
 *
 * <p>{@code --telemetry-syslog HOST:PORT}, {@code --telemetry-jsonl DIR}, and {@code
 * --telemetry-otlp URL} take precedence over profile YAML. Distinct from {@code
 * --telemetry-jsonl-dir} (retention purge only).
 */
public record CliTelemetryOverrides(
        Optional<TelemetryConfig.SyslogSinkConfig> syslog,
        Optional<Path> jsonlDir,
        Optional<TelemetryConfig.OtlpSinkConfig> otlp) {

    public static CliTelemetryOverrides none() {
        return new CliTelemetryOverrides(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return syslog.isEmpty() && jsonlDir.isEmpty() && otlp.isEmpty();
    }

    public TelemetryConfig applyTo(TelemetryConfig yaml) {
        TelemetryConfig base = yaml != null ? yaml : TelemetryConfig.defaults();
        return new TelemetryConfig(
                base.eventsOnly(),
                base.logAggregates(),
                base.sqlitePath(),
                jsonlDir.isPresent() ? jsonlDir : base.jsonlDir(),
                syslog.isPresent() ? syslog : base.syslog(),
                base.gelf(),
                base.loki(),
                otlp.isPresent() ? otlp : base.otlp());
    }

    /**
     * Parses {@code host:port} or {@code [ipv6]:port}. TLS defaults to {@code false} for CLI.
     *
     * @throws IllegalArgumentException when host/port are missing or invalid
     */
    public static TelemetryConfig.SyslogSinkConfig parseSyslogHostPort(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing value for --telemetry-syslog");
        }
        String value = raw.strip();
        String host;
        String portPart;
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 2 || close + 1 >= value.length() || value.charAt(close + 1) != ':') {
                throw new IllegalArgumentException("--telemetry-syslog must be HOST:PORT or [IPv6]:PORT");
            }
            host = value.substring(1, close);
            portPart = value.substring(close + 2);
        } else {
            int colon = value.lastIndexOf(':');
            if (colon <= 0 || colon == value.length() - 1) {
                throw new IllegalArgumentException("--telemetry-syslog must be HOST:PORT or [IPv6]:PORT");
            }
            host = value.substring(0, colon);
            portPart = value.substring(colon + 1);
        }
        if (host.isBlank() || portPart.isBlank()) {
            throw new IllegalArgumentException("--telemetry-syslog must be HOST:PORT or [IPv6]:PORT");
        }
        int port;
        try {
            port = Integer.parseInt(portPart);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("--telemetry-syslog port must be an integer", ex);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("--telemetry-syslog port must be 1..65535");
        }
        return new TelemetryConfig.SyslogSinkConfig(host, port, false);
    }

    /** Parses OTLP base endpoint URL; service name defaults to {@code pingui}. */
    public static TelemetryConfig.OtlpSinkConfig parseOtlpEndpoint(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing value for --telemetry-otlp");
        }
        return new TelemetryConfig.OtlpSinkConfig(raw.strip(), "pingui");
    }
}
