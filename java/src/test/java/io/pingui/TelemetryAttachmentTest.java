package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.TelemetryConfig;
import io.pingui.monitor.MonitorService;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.SqliteTelemetrySink;
import io.pingui.telemetry.TelemetryEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryAttachmentTest {
    @TempDir
    Path tempDir;

    @Test
    void attachWiresSqliteSinkAndPersistsRouteChange() throws Exception {
        Path telemetryDb = tempDir.resolve("gui-telemetry.db");
        TelemetryConfig config = sqliteConfig(telemetryDb);
        MonitorService monitor = new MonitorService(60, 20, 2);
        TelemetryAttachment attachment = TelemetryAttachment.attach(monitor, config, Optional.empty());
        try {
            assertTrue(attachment.registeredIds().contains(SqliteTelemetrySink.ID));
            assertTrue(attachment.registry().contains(SqliteTelemetrySink.ID));
            emitRouteChange(attachment, "1.1.1.1", "2026-07-14T11:00:00Z");
        } finally {
            monitor.close();
            attachment.close();
        }

        try (SessionDatabase db = new SessionDatabase(telemetryDb)) {
            assertEquals(1, db.countTelemetryEvents());
        }
    }

    @Test
    void attachReusesSessionDatabaseWhenPathsMatch() throws Exception {
        Path shared = tempDir.resolve("shared.db");
        try (SessionDatabase sessionDb = new SessionDatabase(shared)) {
            MonitorService monitor = new MonitorService(60, 20, 2);
            TelemetryAttachment attachment =
                    TelemetryAttachment.attach(monitor, sqliteConfig(shared), Optional.of(sessionDb));
            try {
                assertTrue(attachment.registeredIds().contains(SqliteTelemetrySink.ID));
                emitRouteChange(attachment, "8.8.8.8", "2026-07-14T11:05:00Z");
                assertEquals(1, sessionDb.countTelemetryEvents());
            } finally {
                monitor.close();
                attachment.close();
            }
            assertEquals(1, sessionDb.countTelemetryEvents());
        }
    }

    @Test
    void closeOrderKeepsSharedSessionDbUsableUntilStoreClose() throws Exception {
        Path shared = tempDir.resolve("shared-order.db");
        try (SessionDatabase sessionDb = new SessionDatabase(shared)) {
            MonitorService monitor = new MonitorService(60, 20, 2);
            TelemetryAttachment attachment =
                    TelemetryAttachment.attach(monitor, sqliteConfig(shared), Optional.of(sessionDb));
            emitRouteChange(attachment, "9.9.9.9", "2026-07-14T11:10:00Z");
            monitor.close();
            attachment.close();
            assertEquals(1, sessionDb.countTelemetryEvents());
        }
    }

    @Test
    void replaceClosesPreviousAttachmentBeforeNewBus() throws Exception {
        Path firstDb = tempDir.resolve("first.db");
        Path secondDb = tempDir.resolve("second.db");
        MonitorService firstMonitor = new MonitorService(60, 20, 2);
        TelemetryAttachment first = TelemetryAttachment.attach(firstMonitor, sqliteConfig(firstDb), Optional.empty());
        firstMonitor.close();

        MonitorService secondMonitor = new MonitorService(60, 20, 2);
        TelemetryAttachment second =
                TelemetryAttachment.replace(first, secondMonitor, sqliteConfig(secondDb), Optional.empty());
        try {
            emitRouteChange(second, "8.8.4.4", "2026-07-14T11:20:00Z");
        } finally {
            secondMonitor.close();
            second.close();
        }

        try (SessionDatabase db = new SessionDatabase(secondDb)) {
            assertEquals(1, db.countTelemetryEvents());
        }
        try (SessionDatabase db = new SessionDatabase(firstDb)) {
            assertEquals(0, db.countTelemetryEvents());
        }
    }

    private static TelemetryConfig sqliteConfig(Path sqlite) {
        return new TelemetryConfig(
                true,
                false,
                Optional.of(sqlite),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static void emitRouteChange(TelemetryAttachment attachment, String host, String instant) {
        attachment
                .registry()
                .emitEvent(TelemetryEvent.routeChange(
                        host, List.of("10.0.0.1"), List.of(host), Map.of("profile", "gui"), Instant.parse(instant)));
    }
}
