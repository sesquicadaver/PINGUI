package io.pingui.persistence.timeseries;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL / TimescaleDB writer (Python TimescaleTimeSeriesBackend parity).
 *
 * <p>DSN is never logged.
 */
public final class TimescaleTimeSeriesBackend implements TimeSeriesBackend {
    private static final Logger LOG = LoggerFactory.getLogger(TimescaleTimeSeriesBackend.class);
    static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private volatile boolean closed;

    public TimescaleTimeSeriesBackend(String dsn) {
        Objects.requireNonNull(dsn, "dsn");
        if (dsn.isBlank()) {
            throw new TimeSeriesConfigException("Timescale backend requires --timescale-dsn or PINGUI_TIMESCALE_DSN");
        }
        try {
            Properties props = new Properties();
            props.setProperty("loginTimeout", "5");
            props.setProperty("connectTimeout", "5");
            this.connection = DriverManager.getConnection(normalizeJdbcUrl(dsn), props);
            this.connection.setAutoCommit(true);
            initSchema();
        } catch (SQLException ex) {
            throw new TimeSeriesConfigException("Timescale/PostgreSQL connection failed: " + ex.getMessage(), ex);
        }
    }

    TimescaleTimeSeriesBackend(Connection connection) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.connection.setAutoCommit(true);
        initSchema();
    }

    @Override
    public void writePingSamples(List<PingSample> samples) {
        if (samples == null || samples.isEmpty() || closed) {
            return;
        }
        String sql =
                """
                INSERT INTO pingui_ping_samples
                    (time, target_host, hop, hop_ip, rtt_ms)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (PingSample sample : samples) {
                ps.setTimestamp(1, Timestamp.from(sample.observedAt()));
                ps.setString(2, sample.targetHost());
                ps.setInt(3, sample.hop());
                ps.setString(4, sample.hopIp());
                ps.setDouble(5, sample.rttMs());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            LOG.warn("Timescale ping write failed: {}", ex.getMessage());
        }
    }

    @Override
    public void writeRouteEvent(RouteEvent event) {
        if (event == null || closed) {
            return;
        }
        String sql =
                """
                INSERT INTO pingui_route_events
                    (time, target_host, route_ips, route_changed)
                VALUES (?, ?, ?::jsonb, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(event.observedAt()));
            ps.setString(2, event.targetHost());
            ps.setString(3, toJsonArray(event.routeIps()));
            ps.setBoolean(4, event.routeChanged());
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOG.warn("Timescale route write failed: {}", ex.getMessage());
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            connection.close();
        } catch (SQLException ex) {
            LOG.warn("Timescale close failed: {}", ex.getMessage());
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS pingui_schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
            try (ResultSet rs = st.executeQuery("SELECT version FROM pingui_schema_meta LIMIT 1")) {
                if (!rs.next()) {
                    try (PreparedStatement ps =
                            connection.prepareStatement("INSERT INTO pingui_schema_meta(version) VALUES (?)")) {
                        ps.setInt(1, SCHEMA_VERSION);
                        ps.executeUpdate();
                    }
                }
            }
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS pingui_ping_samples (
                        time TIMESTAMPTZ NOT NULL,
                        target_host TEXT NOT NULL,
                        hop INTEGER NOT NULL,
                        hop_ip TEXT NOT NULL,
                        rtt_ms DOUBLE PRECISION NOT NULL
                    )
                    """);
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS pingui_route_events (
                        time TIMESTAMPTZ NOT NULL,
                        target_host TEXT NOT NULL,
                        route_ips JSONB NOT NULL,
                        route_changed BOOLEAN NOT NULL
                    )
                    """);
            ensureHypertables(st);
        }
    }

    private static void ensureHypertables(Statement st) {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'timescaledb'")) {
            if (!rs.next()) {
                return;
            }
        } catch (SQLException ex) {
            return;
        }
        for (String table : List.of("pingui_ping_samples", "pingui_route_events")) {
            try {
                st.execute("SELECT create_hypertable('" + table + "', 'time', if_not_exists => TRUE)");
            } catch (SQLException ex) {
                LOG.debug("create_hypertable skipped for {}: {}", table, ex.getMessage());
            }
        }
    }

    static String toJsonArray(List<String> ips) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < ips.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"')
                    .append(ips.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        out.append(']');
        return out.toString();
    }

    static String normalizeJdbcUrl(String dsn) {
        if (dsn.startsWith("jdbc:")) {
            return dsn;
        }
        if (dsn.startsWith("postgresql://") || dsn.startsWith("postgres://")) {
            return "jdbc:" + dsn.replaceFirst("^postgres://", "postgresql://");
        }
        return dsn;
    }
}
