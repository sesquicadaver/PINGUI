package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.TelemetryConfig;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.SqliteTelemetrySink;
import io.pingui.telemetry.GelfSink;
import io.pingui.telemetry.JsonlRotateSink;
import io.pingui.telemetry.LokiPushSink;
import io.pingui.telemetry.SinkRegistry;
import io.pingui.telemetry.SyslogSink;
import io.pingui.telemetry.TelemetryEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetrySinkInstallerTest {
    @TempDir
    Path tempDir;

    @Test
    void installRegistersLocalAndRemoteSinks() {
        Path sqlite = tempDir.resolve("telemetry.db");
        Path jsonl = tempDir.resolve("jsonl");
        TelemetryConfig config = new TelemetryConfig(
                true,
                false,
                Optional.of(sqlite),
                Optional.of(jsonl),
                Optional.of(new TelemetryConfig.SyslogSinkConfig("127.0.0.1", 1514, false)),
                Optional.of(new TelemetryConfig.GelfSinkConfig("127.0.0.1", 12201, GelfSink.Transport.TCP)),
                Optional.of(new TelemetryConfig.LokiSinkConfig("http://127.0.0.1:3100", "lab")));
        try (SinkRegistry registry = new SinkRegistry()) {
            TelemetrySinkInstaller.Result result = TelemetrySinkInstaller.install(registry, config, Optional.empty());
            assertTrue(registry.contains(SqliteTelemetrySink.ID));
            assertTrue(registry.contains(JsonlRotateSink.ID));
            assertTrue(registry.contains(SyslogSink.ID));
            assertTrue(registry.contains(GelfSink.ID));
            assertTrue(registry.contains(LokiPushSink.ID));
            assertEquals(5, result.registeredIds().size());
            assertEquals(1, result.ownedResources().size());
            result.closeOwned();
        }
    }

    @Test
    void installReusesSessionDatabaseWhenPathsMatch() throws Exception {
        Path dbPath = tempDir.resolve("shared.db");
        try (SessionDatabase sessionDb = new SessionDatabase(dbPath);
                SinkRegistry registry = new SinkRegistry()) {
            TelemetryConfig config = new TelemetryConfig(
                    true,
                    false,
                    Optional.of(dbPath),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
            TelemetrySinkInstaller.Result result =
                    TelemetrySinkInstaller.install(registry, config, Optional.of(sessionDb));
            assertTrue(result.ownedResources().isEmpty());
            assertTrue(registry.contains(SqliteTelemetrySink.ID));
            registry.emitEvent(TelemetryEvent.routeChange(
                    "8.8.8.8",
                    List.of("10.0.0.1"),
                    List.of("8.8.8.8"),
                    Map.of("profile", "t"),
                    Instant.parse("2026-07-14T09:00:00Z")));
            assertEquals(1, sessionDb.countTelemetryEvents());
            result.closeOwned();
        }
    }

    @Test
    void defaultsInstallNothing() {
        try (SinkRegistry registry = new SinkRegistry()) {
            TelemetrySinkInstaller.Result result =
                    TelemetrySinkInstaller.install(registry, TelemetryConfig.defaults(), Optional.empty());
            assertEquals(0, registry.size());
            assertTrue(result.registeredIds().isEmpty());
            assertTrue(result.ownedResources().isEmpty());
            assertFalse(registry.contains(SqliteTelemetrySink.ID));
        }
    }
}
