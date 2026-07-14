package io.pingui.config;

import io.pingui.telemetry.GelfSink;
import io.pingui.telemetry.SinkConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-profile telemetry YAML ({@code telemetry:}) — P16-040 / ADR_TELEMETRY.
 *
 * <p>Default: all local/remote sinks off, {@code events_only=true}, {@code log_aggregates=false}.
 * Presence of {@code sqlite}/{@code jsonl_dir}/syslog/gelf/loki/otlp entries enables that sink in
 * the config model. CLI overrides: P16-041 / P16-080. Secret redaction for logs: {@link
 * #redactUrl(String)}, {@link #redactSecret(String)}, {@link #toRedactedString()} (P16-042).
 */
public record TelemetryConfig(
        boolean eventsOnly,
        boolean logAggregates,
        Optional<Path> sqlitePath,
        Optional<Path> jsonlDir,
        Optional<SyslogSinkConfig> syslog,
        Optional<GelfSinkConfig> gelf,
        Optional<LokiSinkConfig> loki,
        Optional<OtlpSinkConfig> otlp) {

    public TelemetryConfig {
        sqlitePath = sqlitePath != null ? sqlitePath : Optional.empty();
        jsonlDir = jsonlDir != null ? jsonlDir : Optional.empty();
        syslog = syslog != null ? syslog : Optional.empty();
        gelf = gelf != null ? gelf : Optional.empty();
        loki = loki != null ? loki : Optional.empty();
        otlp = otlp != null ? otlp : Optional.empty();
    }

    public static TelemetryConfig defaults() {
        return new TelemetryConfig(
                true,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
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
                && loki.isEmpty()
                && otlp.isEmpty();
    }

    public TelemetryConfig withEventsOnly(boolean eventsOnly) {
        return new TelemetryConfig(eventsOnly, logAggregates, sqlitePath, jsonlDir, syslog, gelf, loki, otlp);
    }

    public TelemetryConfig withLogAggregates(boolean logAggregates) {
        return new TelemetryConfig(eventsOnly, logAggregates, sqlitePath, jsonlDir, syslog, gelf, loki, otlp);
    }

    /**
     * Log-safe URL: scheme + host[:port] + path; strips userinfo and query (P16-042).
     *
     * @return empty string for null/blank; {@code <invalid-url>} when unparsable
     */
    public static String redactUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(url.strip());
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost() != null ? uri.getHost() : "unknown";
            int port = uri.getPort();
            String path = uri.getRawPath() != null ? uri.getRawPath() : "";
            if (port > 0) {
                return scheme + "://" + host + ":" + port + path;
            }
            return scheme + "://" + host + path;
        } catch (URISyntaxException ex) {
            return "<invalid-url>";
        }
    }

    /**
     * Masks a bearer/token/password for debug logs (P16-042). Never returns the full secret.
     */
    public static String redactSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        String value = secret.strip();
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "…" + "****";
    }

    /** Debug-safe summary: sink endpoints without credentials or query secrets. */
    public String toRedactedString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("TelemetryConfig{eventsOnly=")
                .append(eventsOnly)
                .append(", logAggregates=")
                .append(logAggregates);
        sqlitePath.ifPresent(path -> sb.append(", sqlite=").append(path));
        jsonlDir.ifPresent(path -> sb.append(", jsonlDir=").append(path));
        syslog.ifPresent(s -> sb.append(", syslog=")
                .append(s.host())
                .append(':')
                .append(s.port())
                .append(s.tls() ? "(tls)" : ""));
        gelf.ifPresent(g -> sb.append(", gelf=")
                .append(g.host())
                .append(':')
                .append(g.port())
                .append('/')
                .append(g.transport().name().toLowerCase(Locale.ROOT)));
        loki.ifPresent(l ->
                sb.append(", loki=").append(redactUrl(l.url())).append(" site=").append(l.site()));
        otlp.ifPresent(o -> sb.append(", otlp=")
                .append(redactUrl(o.endpoint()))
                .append(" service=")
                .append(o.serviceName()));
        sb.append('}');
        return sb.toString();
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

    /** OTLP/HTTP endpoint from YAML {@code telemetry.otlp} (P16-080). */
    public record OtlpSinkConfig(String endpoint, String serviceName) {
        public OtlpSinkConfig {
            endpoint = requireNonBlank(endpoint, "telemetry.otlp.endpoint");
            serviceName = serviceName == null || serviceName.isBlank() ? "pingui" : serviceName.strip();
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
