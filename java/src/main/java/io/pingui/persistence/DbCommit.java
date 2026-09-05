package io.pingui.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Shared transaction state: wraps a {@link Connection} with a deferred-commit flag and
 * utility helpers (ISO-UTC formatter, nullable double) used across all persistence repositories.
 *
 * <p>Package-private — all external access goes through {@link SessionDatabase}.
 */
final class DbCommit {

    /** ISO-8601 UTC formatter used for all TEXT timestamp columns. */
    static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    /** Live JDBC connection; never replaced after construction. */
    final Connection connection;

    /**
     * When {@code true}, {@link #maybeCommit()} is a no-op — an outer
     * {@link SessionDatabase#inTransaction} owns the commit boundary.
     */
    boolean deferCommit;

    DbCommit(Connection connection) {
        this.connection = connection;
    }

    /**
     * Commits the current transaction unless deferred (inside
     * {@link SessionDatabase#inTransaction}).
     */
    void maybeCommit() throws SQLException {
        if (!deferCommit) {
            connection.commit();
        }
    }

    /** Best-effort rollback — silently ignores {@link SQLException}. */
    void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort after failure.
        }
    }

    /**
     * Returns {@code null} when the column value is SQL NULL, otherwise the {@code double} value.
     */
    static Double nullableDouble(ResultSet rs, int column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : rs.getDouble(column);
    }

    /** Sets parameter {@code index} to SQL NULL when {@code value} is {@code null}. */
    static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setDouble(index, value);
        }
    }
}
