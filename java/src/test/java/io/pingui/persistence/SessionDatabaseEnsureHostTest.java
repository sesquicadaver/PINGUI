package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.pingui.model.Models.HostSessionData;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDatabaseEnsureHostTest {
    @TempDir
    Path tempDir;

    @Test
    void ensureHostExistsDoesNotLoadPayloadAndIsIdempotent() {
        Path dbPath = tempDir.resolve("ensure.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertNull(db.load("new.example"));
            db.ensureHostExists("new.example");
            HostSessionData loaded = db.load("new.example");
            assertNotNull(loaded);
            assertEquals(true, loaded.isEnabled());
            assertEquals(0, loaded.getCurrentRoute().size());
            db.ensureHostExists("new.example");
            assertEquals(1, db.listHosts().size());
        }
    }
}
