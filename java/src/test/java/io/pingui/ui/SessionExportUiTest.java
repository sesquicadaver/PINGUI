package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SessionExportUiTest {
    @Test
    void noSqliteMessagePointsToDatabaseMenu() {
        String message = SessionExportUi.noSqliteMessage();
        assertTrue(message.contains("SQLite"));
        assertTrue(message.contains("База даних"));
        assertFalse(message.isBlank());
    }

    @Test
    void successAndFailureMessagesAreNonEmpty() {
        assertTrue(SessionExportUi.successMessage(Path.of("report.csv")).contains("report.csv"));
        assertTrue(SessionExportUi.failureMessage(new RuntimeException("disk full"))
                .contains("disk full"));
    }
}
