package io.pingui.telemetry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;

/**
 * Remote GELF 1.1 sink (P16-031 / ADR_TELEMETRY / SPIKE_LOG_SINKS).
 *
 * <p>TCP (production) frames JSON with a trailing NUL ({@code \0}). UDP (lab) sends a single
 * datagram without chunking. {@link #eventsOnly()} is {@code true}. Failures are logged; methods
 * never throw into the poll / bus path.
 */
public final class GelfSink implements TelemetrySink {
    public static final String ID = "gelf";

    private static final Logger LOG = Logger.getLogger(GelfSink.class.getName());

    /** Wire transport for GELF v1. */
    public enum Transport {
        TCP,
        UDP
    }

    private final String host;
    private final int port;
    private final Transport transport;
    private final String sourceHost;
    private final SocketFactory socketFactory;
    private final Supplier<DatagramSocket> datagramSocketSupplier;
    private final Clock clock;

    private final Object lock = new Object();
    private Socket tcpSocket;
    private OutputStream tcpOut;

    public GelfSink(String host, int port) {
        this(host, port, Transport.TCP);
    }

    public GelfSink(String host, int port, Transport transport) {
        this(
                host,
                port,
                transport,
                detectHostname(),
                SocketFactory.getDefault(),
                GelfSink::newDatagramSocket,
                Clock.systemUTC());
    }

    private static DatagramSocket newDatagramSocket() {
        try {
            return new DatagramSocket();
        } catch (SocketException ex) {
            throw new IllegalStateException("unable to create DatagramSocket", ex);
        }
    }

    /** Full constructor for tests (injectable sockets / clock / source host). */
    public GelfSink(
            String host,
            int port,
            Transport transport,
            String sourceHost,
            SocketFactory socketFactory,
            Supplier<DatagramSocket> datagramSocketSupplier,
            Clock clock) {
        this.host = requireNonBlank(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1..65535");
        }
        this.port = port;
        this.transport = Objects.requireNonNull(transport, "transport");
        this.sourceHost = requireNonBlank(sourceHost, "sourceHost");
        this.socketFactory = Objects.requireNonNull(socketFactory, "socketFactory");
        this.datagramSocketSupplier = Objects.requireNonNull(datagramSocketSupplier, "datagramSocketSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean eventsOnly() {
        return true;
    }

    @Override
    public void onSample(MetricSample sample) {
        // intentionally empty: events_only — high-freq samples never leave this sink
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        try {
            byte[] payload = formatPayload(event).getBytes(StandardCharsets.UTF_8);
            if (transport == Transport.TCP) {
                sendTcp(payload);
            } else {
                sendUdp(payload);
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "GelfSink write failed to " + host + ":" + port, ex);
            closeTcpQuietly();
        }
    }

    @Override
    public void close() {
        closeTcpQuietly();
    }

    /** Builds one GELF 1.1 JSON object (no NUL terminator). Package-visible for tests. */
    String formatPayload(TelemetryEvent event) {
        Instant ts = event.timestamp() != null ? event.timestamp() : clock.instant();
        int level = SyslogSink.severityFor(event.event());
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        append(sb, "version", "1.1", true);
        append(sb, "host", event.host(), false);
        append(sb, "short_message", event.event(), false);
        if (event.message() != null) {
            append(sb, "full_message", event.message(), false);
        }
        sb.append(",\"timestamp\":").append(formatEpochSeconds(ts));
        sb.append(",\"level\":").append(level);
        append(sb, "_event", event.event(), false);
        append(sb, "_source", sourceHost, false);
        for (Map.Entry<String, String> label : event.labels().entrySet()) {
            append(sb, additionalKey(label.getKey()), label.getValue(), false);
        }
        if (!event.oldIps().isEmpty()) {
            sb.append(",\"_old_ips\":").append(TelemetryJson.stringArray(event.oldIps()));
        }
        if (!event.newIps().isEmpty()) {
            sb.append(",\"_new_ips\":").append(TelemetryJson.stringArray(event.newIps()));
        }
        append(sb, "_payload", event.toJson(), false);
        sb.append('}');
        return sb.toString();
    }

    private void sendTcp(byte[] jsonUtf8) throws IOException {
        synchronized (lock) {
            ensureTcp();
            tcpOut.write(jsonUtf8);
            tcpOut.write(0);
            tcpOut.flush();
        }
    }

    private void sendUdp(byte[] jsonUtf8) throws IOException {
        InetAddress address = InetAddress.getByName(host);
        try (DatagramSocket datagram = datagramSocketSupplier.get()) {
            datagram.send(new DatagramPacket(jsonUtf8, jsonUtf8.length, address, port));
        }
    }

    private void ensureTcp() throws IOException {
        if (tcpSocket != null && tcpSocket.isConnected() && !tcpSocket.isClosed() && tcpOut != null) {
            return;
        }
        closeTcpQuietlyUnlocked();
        Socket created = socketFactory.createSocket(host, port);
        created.setTcpNoDelay(true);
        tcpSocket = created;
        tcpOut = created.getOutputStream();
    }

    private void closeTcpQuietly() {
        synchronized (lock) {
            closeTcpQuietlyUnlocked();
        }
    }

    private void closeTcpQuietlyUnlocked() {
        if (tcpOut != null) {
            try {
                tcpOut.close();
            } catch (IOException ignored) {
                // best effort
            }
            tcpOut = null;
        }
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException ignored) {
                // best effort
            }
            tcpSocket = null;
        }
    }

    private static void append(StringBuilder sb, String key, String value, boolean first) {
        if (!first) {
            sb.append(',');
        }
        sb.append(TelemetryJson.quote(key)).append(':').append(TelemetryJson.quote(value));
    }

    private static String additionalKey(String raw) {
        String key = raw == null ? "" : raw.strip();
        if (key.isEmpty() || "id".equalsIgnoreCase(key) || "_id".equalsIgnoreCase(key)) {
            return "_label";
        }
        if (key.startsWith("_")) {
            return key;
        }
        return "_" + key;
    }

    private static String formatEpochSeconds(Instant instant) {
        long seconds = instant.getEpochSecond();
        int nanos = instant.getNano();
        if (nanos == 0) {
            return Long.toString(seconds);
        }
        // keep millisecond precision for stable tests / Graylog
        long millis = nanos / 1_000_000L;
        return seconds + "." + String.format(Locale.ROOT, "%03d", millis);
    }

    private static String detectHostname() {
        try {
            String name = InetAddress.getLocalHost().getHostName();
            if (name != null && !name.isBlank()) {
                return name.strip().replace(' ', '_').toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "localhost";
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
