package io.pingui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.HopStats;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionReportExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void isHtmlReportMatchesHtmlExtensionsOnly() {
        assertTrue(SessionReportExporter.isHtmlReport(Path.of("a.html")));
        assertTrue(SessionReportExporter.isHtmlReport(Path.of("B.HTM")));
        assertFalse(SessionReportExporter.isHtmlReport(Path.of("a.csv")));
        assertFalse(SessionReportExporter.isHtmlReport(Path.of("report.txt")));
        assertFalse(SessionReportExporter.isHtmlReport(Path.of("/")));
        assertTrue(SessionReportExporter.isHtmlReport(Path.of("Report.HTML")));
    }

    @Test
    void exportChoosesFormatByExtension() throws Exception {
        Path dbPath = tempDir.resolve("session.db");
        seedSampleHost(dbPath, "8.8.8.8");
        Path csvPath = tempDir.resolve("out.csv");
        Path htmlPath = tempDir.resolve("out.html");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            SessionReportExporter.export(database, csvPath);
            SessionReportExporter.export(database, htmlPath);
        }
        assertTrue(Files.readString(csvPath).contains("route_kind"));
        assertTrue(Files.readString(htmlPath).contains("<title>PINGUI session report</title>"));
    }

    @Test
    void exportCsvContainsRouteRows() throws Exception {
        Path dbPath = tempDir.resolve("session.db");
        seedSampleHost(dbPath, "8.8.8.8");

        Path csvPath = tempDir.resolve("report.csv");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            SessionReportExporter.exportCsv(database, csvPath);
        }

        String text = Files.readString(csvPath);
        assertTrue(text.contains("route_kind"));
        assertTrue(text.contains("10.0.0.1"));
        assertTrue(text.contains("current"));
    }

    @Test
    void exportHtmlContainsHostTable() throws Exception {
        Path dbPath = tempDir.resolve("session.db");
        seedSampleHost(dbPath, "8.8.8.8");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            HostSessionData empty = new HostSessionData();
            empty.setEnabled(false);
            database.save("1.1.1.1", empty);
        }

        Path htmlPath = tempDir.resolve("report.html");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            SessionReportExporter.exportHtml(database, htmlPath);
        }

        String text = Files.readString(htmlPath);
        assertTrue(text.contains("<title>PINGUI session report</title>"));
        assertTrue(text.contains("8.8.8.8"));
        assertTrue(text.contains("10.0.0.1"));
        assertTrue(text.contains("1.1.1.1"));
    }

    @Test
    void buildRouteRowsIncludesInactiveWhenLastKnownPresent() throws Exception {
        Path dbPath = tempDir.resolve("inactive.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            data.setCurrentRoute(List.of(new HopNode(1, "10.0.0.2", 6.0, false)));
            data.setPreviousRoute(List.of(new HopNode(1, "*", null, true)));
            data.getLastKnownByHop().put(1, new HopNode(1, "10.0.0.1", 5.0, false));
            database.save("host", data);

            List<SessionReportRouteRow> rows = SessionReportExporter.buildRouteRows(database);
            assertEquals(2, rows.size());
            assertTrue(rows.stream().anyMatch(row -> "current".equals(row.routeKind())));
            assertTrue(
                    rows.stream().anyMatch(row -> "inactive".equals(row.routeKind()) && "10.0.0.1".equals(row.ip())));
        }
    }

    @Test
    void buildRouteRowsIncludesHopStats() throws Exception {
        Path dbPath = tempDir.resolve("stats.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            data.setCurrentRoute(List.of(new HopNode(1, "8.8.8.8", 10.0, false)));
            HopProbeStats stats = new HopProbeStats();
            HopStats.recordProbe(stats, new HopNode(1, "8.8.8.8", 10.0, false));
            HopStats.recordProbe(stats, new HopNode(1, "8.8.8.8", 14.0, false));
            data.getHopStats().put(1, stats);
            database.save("host", data);

            SessionReportRouteRow row =
                    SessionReportExporter.buildRouteRows(database).get(0);
            assertEquals(2.0, row.jitterMs());
            assertEquals(0.0, row.lossPct());
        }
    }

    private static void seedSampleHost(Path dbPath, String host) throws Exception {
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            data.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, host, 10.0, false)));
            data.getPingHistory().put("10.0.0.1", List.of(4.0, 6.0));
            database.save(host, data);
        }
    }
}
