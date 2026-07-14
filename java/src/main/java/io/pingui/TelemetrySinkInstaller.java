package io.pingui;

import io.pingui.config.TelemetryConfig;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.SqliteTelemetrySink;
import io.pingui.telemetry.GelfSink;
import io.pingui.telemetry.JsonlRotateSink;
import io.pingui.telemetry.LokiPushSink;
import io.pingui.telemetry.SinkConfig;
import io.pingui.telemetry.SinkRegistry;
import io.pingui.telemetry.SyslogSink;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers local/remote telemetry sinks from {@link TelemetryConfig} onto a {@link SinkRegistry}
 * (P16-071 / ADR_TELEMETRY).
 *
 * <p>Owned resources (extra SQLite DB when {@code telemetry.sqlite} is not the session DB) must be
 * closed by the caller after the registry. Sink failures stay isolated inside each sink /
 * {@link SinkRegistry}.
 */
public final class TelemetrySinkInstaller {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetrySinkInstaller.class);

    private TelemetrySinkInstaller() {}

    /**
     * Registers sinks enabled in {@code config}. Reuses {@code sessionDb} when its path matches
     * {@code telemetry.sqlite}; otherwise opens a dedicated {@link SessionDatabase}.
     *
     * @return registration result including AutoCloseable resources owned by the installer
     */
    public static Result install(SinkRegistry registry, TelemetryConfig config, Optional<SessionDatabase> sessionDb) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(sessionDb, "sessionDb");

        List<AutoCloseable> owned = new ArrayList<>();
        List<String> registered = new ArrayList<>();
        SinkConfig remotePolicy = config.toSinkConfig();

        config.sqlitePath().ifPresent(path -> {
            SessionDatabase db = resolveSqlite(path, sessionDb, owned);
            registry.register(new SqliteTelemetrySink(db));
            registered.add(SqliteTelemetrySink.ID);
            LOG.info(
                    "Telemetry sqlite sink enabled ({})",
                    db.path().toAbsolutePath().normalize());
        });

        config.jsonlDir().ifPresent(dir -> {
            registry.register(new JsonlRotateSink(dir));
            registered.add(JsonlRotateSink.ID);
            LOG.info("Telemetry jsonl sink enabled ({})", dir.toAbsolutePath().normalize());
        });

        config.syslog().ifPresent(syslog -> {
            registry.register(new SyslogSink(syslog.host(), syslog.port(), syslog.tls(), remotePolicy));
            registered.add(SyslogSink.ID);
            LOG.info(
                    "Telemetry syslog sink enabled ({}:{}{})",
                    syslog.host(),
                    syslog.port(),
                    syslog.tls() ? " tls" : "");
        });

        config.gelf().ifPresent(gelf -> {
            registry.register(new GelfSink(gelf.host(), gelf.port(), gelf.transport(), remotePolicy));
            registered.add(GelfSink.ID);
            LOG.info(
                    "Telemetry gelf sink enabled ({}:{} {})",
                    gelf.host(),
                    gelf.port(),
                    gelf.transport().name().toLowerCase(Locale.ROOT));
        });

        config.loki().ifPresent(loki -> {
            registry.register(new LokiPushSink(loki.url(), loki.site(), remotePolicy));
            registered.add(LokiPushSink.ID);
            LOG.info("Telemetry loki sink enabled ({} site={})", TelemetryConfig.redactUrl(loki.url()), loki.site());
        });

        return new Result(List.copyOf(owned), List.copyOf(registered));
    }

    private static SessionDatabase resolveSqlite(
            Path configured, Optional<SessionDatabase> sessionDb, List<AutoCloseable> owned) {
        Path wanted = configured.toAbsolutePath().normalize();
        if (sessionDb.isPresent()) {
            Path sessionPath = sessionDb.get().path().toAbsolutePath().normalize();
            if (sessionPath.equals(wanted)) {
                return sessionDb.get();
            }
        }
        SessionDatabase db = new SessionDatabase(wanted);
        owned.add(db);
        return db;
    }

    /** Result of {@link #install}: owned closeables + registered sink ids. */
    public record Result(List<AutoCloseable> ownedResources, List<String> registeredIds) {
        public Result {
            ownedResources = List.copyOf(ownedResources);
            registeredIds = List.copyOf(registeredIds);
        }

        /** Closes owned resources; sink close is owned by {@link SinkRegistry}. */
        public void closeOwned() {
            for (AutoCloseable resource : ownedResources) {
                try {
                    resource.close();
                } catch (Exception ex) {
                    LOG.warn("Failed to close telemetry owned resource: {}", ex.toString());
                }
            }
        }
    }
}
