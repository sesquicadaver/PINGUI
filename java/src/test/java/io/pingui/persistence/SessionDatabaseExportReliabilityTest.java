package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.export.SessionReportExporter;
import io.pingui.model.Models.HostSessionData;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P30-006: read-only export connection and integrity_check. */
class SessionDatabaseExportReliabilityTest {
    @TempDir
    Path tempDir;

    @Test
    void integrityCheckOkOnFreshDatabase() {
        Path dbPath = tempDir.resolve("ok.db");
        try (SessionDatabase rw = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            rw.save("8.8.8.8", data);
        }
        try (SessionDatabase ro = SessionDatabase.readOnly(dbPath)) {
            assertEquals(SessionDatabase.OpenMode.READ_ONLY, ro.openMode());
            IntegrityCheckResult result = ro.integrityCheck();
            assertTrue(result.ok());
            assertEquals(1, result.messages().size());
            assertEquals("ok", result.messages().get(0));
        }
    }

    @Test
    void readOnlyRejectsMissingFile() {
        Path missing = tempDir.resolve("missing.db");
        PersistenceException ex = assertThrows(PersistenceException.class, () -> SessionDatabase.readOnly(missing));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void readOnlyExportWhileWriterOpen() throws Exception {
        Path dbPath = tempDir.resolve("concurrent.db");
        try (SessionDatabase writer = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            writer.save("1.1.1.1", data);
            Path csv = tempDir.resolve("report.csv");
            try (SessionDatabase reader = SessionDatabase.readOnly(dbPath)) {
                SessionReportExporter.exportCsv(reader, csv);
            }
            assertTrue(Files.size(csv) > 0);
            writer.save("8.8.8.8", data);
        }
    }
}
