package io.pingui.ui;

import io.pingui.export.SessionReportExporter;
import io.pingui.i18n.UiI18n;
import io.pingui.persistence.SessionDatabase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * GUI «Експорт зараз…»: FileChooser + {@link SessionReportExporter} (CLI format parity).
 * Copy for missing SQLite / success / failure is unit-tested without showing dialogs.
 */
final class SessionExportUi {
    private SessionExportUi() {}

    /** Clear error when session SQLite is not connected. */
    static String noSqliteMessage() {
        return UiI18n.get("persistence.export_no_sqlite");
    }

    static String successMessage(Path path) {
        return UiI18n.get("persistence.export_success", path.toAbsolutePath());
    }

    static String failureMessage(Throwable error) {
        String detail = error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
        return UiI18n.get("persistence.export_failed", detail);
    }

    /**
     * Shows save dialog and writes CSV/HTML by extension. Empty if cancelled.
     *
     * @throws IOException on write failure after a path was chosen
     */
    static Optional<Path> chooseAndExport(Window owner, SessionDatabase database) throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(UiI18n.get("persistence.export_title"));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(UiI18n.get("dialog.filter_csv"), "*.csv"));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(UiI18n.get("dialog.filter_html"), "*.html", "*.htm"));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(UiI18n.get("dialog.filter_all_files"), "*.*"));
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
