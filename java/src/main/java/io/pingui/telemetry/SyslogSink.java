package io.pingui.telemetry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

/**
 * Remote RFC 5424 syslog sink over TCP (P16-030 / ADR_TELEMETRY / SPIKE_LOG_SINKS).
 *
 * <p>MSG is single-line {@link TelemetryEvent#toJson()}. TCP framing is RFC 6587
 * <em>non-transparent</em> (trailing {@code \n}). {@link #eventsOnly()} comes from
 * {@link SinkConfig} (default {@code true}). TLS is optional via {@link SSLSocketFactory}. Failures
 * are logged; methods never throw into the poll / bus path.
 */
public final class SyslogSink implements TelemetrySink {
    public static final String ID = "syslog";
    /** RFC 5424 facility LOCAL0. */
    public static final int FACILITY_LOCAL0 = 16;
    /** RFC 5424 severity NOTICE. */
    public static final int SEVERITY_NOTICE = 5;
    /** RFC 5424 severity WARNING. */
    public static final int SEVERITY_WARNING = 4;

    private static final Logger LOG = Logger.getLogger(SyslogSink.class.getName());
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final String host;
    private final int port;
    private final boolean tls;
    private final String appName;
    private final String hostname;
    private final SocketFactory socketFactory;
    private final Clock clock;
    private final boolean eventsOnly;

    private final Object lock = new Object();
    private Socket socket;
    private OutputStream out;

    public SyslogSink(String host, int port) {
        this(host, port, false);
    }

    public SyslogSink(String host, int port, boolean tls) {
        this(host, port, tls, SinkConfig.defaults());
    }

    public SyslogSink(String host, int port, boolean tls, SinkConfig sinkConfig) {
        this(host, port, tls, "pingui", detectHostname(), socketFactoryFor(tls), Clock.systemUTC(), sinkConfig);
    }

    /**
     * Full constructor for tests (injectable factory / clock / hostname).
     *
     * @param appName RFC 5424 APP-NAME (non-blank)
     * @param hostname RFC 5424 HOSTNAME (non-blank)
     */
    public SyslogSink(
            String host,
            int port,
            boolean tls,
            String appName,
            String hostname,
            SocketFactory socketFactory,
            Clock clock) {
        this(host, port, tls, appName, hostname, socketFactory, clock, SinkConfig.defaults());
    }

    public SyslogSink(
            String host,
            int port,
            boolean tls,
            String appName,
            String hostname,
            SocketFactory socketFactory,
            Clock clock,
            SinkConfig sinkConfig) {
        this.host = Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must be non-blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1..65535");
        }
        this.port = port;
        this.tls = tls;
        this.appName = requireToken(appName, "appName");
        this.hostname = requireToken(hostname, "hostname");
        this.socketFactory = Objects.requireNonNull(socketFactory, "socketFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventsOnly = SinkConfig.require(sinkConfig).eventsOnly();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean eventsOnly() {
        return eventsOnly;
    }

    @Override
    public void onSample(MetricSample sample) {
        if (eventsOnly || sample == null) {
            return;
        }
        writeLine(formatSampleMessage(sample));
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        writeLine(formatMessage(event));
    }

    private void writeLine(String line) {
        try {
            byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
            synchronized (lock) {
                ensureConnected();
                out.write(bytes);
                out.flush();
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "SyslogSink write failed to " + host + ":" + port, ex);
            closeQuietly();
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    /** Formats one RFC 5424 line without the trailing NL (package-visible for tests). */
    String formatMessage(TelemetryEvent event) {
        return formatSyslog(severityFor(event.event()), event.timestamp(), event.toJson());
    }

    /** Sample MSG path when {@code events_only=false} (lab). Package-visible for tests. */
    String formatSampleMessage(MetricSample sample) {
        return formatSyslog(SEVERITY_NOTICE, sample.timestamp(), sample.toJson());
    }

    private String formatSyslog(int severity, Instant timestamp, String jsonMsg) {
        int pri = FACILITY_LOCAL0 * 8 + severity;
        Instant ts = timestamp != null ? timestamp : clock.instant();
        String msg = jsonMsg;
        if (msg.indexOf('\n') >= 0 || msg.indexOf('\r') >= 0) {
            msg = msg.replace("\r", "").replace("\n", " ");
        }
        return "<" + pri + ">1 " + TIMESTAMP.format(ts) + " " + hostname + " " + appName + " - - - " + msg;
    }

    static int severityFor(String eventType) {
        if (TelemetryEvent.PROBE_ERROR.equals(eventType)) {
            return SEVERITY_WARNING;
        }
        return SEVERITY_NOTICE;
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed() && out != null) {
            return;
        }
        closeQuietlyUnlocked();
        Socket created = socketFactory.createSocket(host, port);
        created.setTcpNoDelay(true);
        socket = created;
        out = created.getOutputStream();
        if (tls) {
            LOG.fine("SyslogSink TLS socket opened to " + host + ":" + port);
        }
    }

    private void closeQuietly() {
        synchronized (lock) {
            closeQuietlyUnlocked();
        }
    }

    private void closeQuietlyUnlocked() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // best effort
            }
            out = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
            socket = null;
        }
    }

    private static SocketFactory socketFactoryFor(boolean useTls) {
        return useTls ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
    }

    private static String detectHostname() {
        try {
            String name = InetAddress.getLocalHost().getHostName();
            if (name != null && !name.isBlank()) {
                return sanitizeHostname(name);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "localhost";
    }

    private static String sanitizeHostname(String raw) {
        String cleaned = raw.strip().replace(' ', '_');
        if (cleaned.isEmpty()) {
            return "localhost";
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private static String requireToken(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.contains(" ") || trimmed.contains("\n")) {
            throw new IllegalArgumentException(field + " must be a single non-blank token");
        }
        return trimmed;
    }
}
