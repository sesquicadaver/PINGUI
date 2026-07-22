package io.pingui.ui;

import io.pingui.export.SessionReportExporter;
import io.pingui.persistence.SessionDatabase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * GUI «Експорт зараз…»: FileChooser + {@link SessionReportExporter} (CLI format parity).
 * UK copy for missing SQLite / success / failure is unit-tested without showing dialogs.
 */
final class SessionExportUi {
    private SessionExportUi() {}

    /** Clear error when session SQLite is not connected. */
    static String noSqliteMessage() {
        return "Експорт потребує SQLite session. Налаштування → База даних…";
    }

    static String successMessage(Path path) {
        return "Звіт збережено: " + path.toAbsolutePath();
    }

    static String failureMessage(Throwable error) {
        String detail = error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
        return "Експорт не вдався: " + detail;
    }

    /**
     * Shows save dialog and writes CSV/HTML by extension. Empty if cancelled.
     *
     * @throws IOException on write failure after a path was chosen
     */
    static Optional<Path> chooseAndExport(Window owner, SessionDatabase database) throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Експорт звіту сесії");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML (*.html)", "*.html", "*.htm"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Усі файли", "*.*"));
        chooser.setInitialFileName("pingui-session-report.csv");
        File chosen = chooser.showSaveDialog(owner);
        if (chosen == null) {
            return Optional.empty();
        }
        Path path = chosen.toPath();
        SessionReportExporter.export(database, path);
        return Optional.of(path);
    }
}
