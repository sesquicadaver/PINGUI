package io.pingui.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Load/save {@link WindowGeometry} under the user config directory (XDG / APPDATA). */
final class WindowGeometryStore {
    private static final Logger LOG = Logger.getLogger(WindowGeometryStore.class.getName());
    private static final String FILE_NAME = "window-geometry.properties";

    private final Path file;

    WindowGeometryStore(Path file) {
        this.file = file;
    }

    static WindowGeometryStore userDefault() {
        return new WindowGeometryStore(defaultFile());
    }

    static Path defaultFile() {
        return configDir().resolve(FILE_NAME);
    }

    static Path configDir() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg, "pingui");
        }
        String appData = System.getenv("APPDATA");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (appData != null && !appData.isBlank() && os.contains("win")) {
            return Path.of(appData, "pingui");
        }
        return Path.of(System.getProperty("user.home"), ".config", "pingui");
    }

    Path file() {
        return file;
    }

    /**
     * Loads geometry or returns Simple defaults when the file is missing/unreadable.
     *
     * @param defaultWidthSimple fallback width for Simple / missing file
     * @param defaultWidthExtended fallback width when saved mode is Extended and width is missing
     * @param defaultHeightSimple fallback height for Simple / missing file
     * @param defaultHeightExtended fallback height when saved mode is Extended and height is missing
     */
    WindowGeometry load(
            double defaultWidthSimple,
            double defaultWidthExtended,
            double defaultHeightSimple,
            double defaultHeightExtended) {
        WindowGeometry defaults = WindowGeometry.defaults(defaultWidthSimple, defaultHeightSimple);
        if (!Files.isRegularFile(file)) {
            return defaults;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to read window geometry: " + file, ex);
            return defaults;
        }
        UiViewMode mode = parseMode(props.getProperty("viewMode"));
        double fallbackWidth = mode == UiViewMode.EXTENDED ? defaultWidthExtended : defaultWidthSimple;
        double fallbackHeight = mode == UiViewMode.EXTENDED ? defaultHeightExtended : defaultHeightSimple;
        double x = parseDouble(props.getProperty("x"), Double.NaN);
        double y = parseDouble(props.getProperty("y"), Double.NaN);
        double width = parseDouble(props.getProperty("width"), fallbackWidth);
        double height = parseDouble(props.getProperty("height"), fallbackHeight);
        double divider = parseDouble(props.getProperty("divider"), WindowGeometry.DEFAULT_DIVIDER);
        return new WindowGeometry(x, y, width, height, divider, mode);
    }

    /** Writes geometry; IO failures are logged and swallowed (close path must not crash). */
    void save(WindowGeometry geometry) {
        Properties props = new Properties();
        if (!Double.isNaN(geometry.x())) {
            props.setProperty("x", format(geometry.x()));
        }
        if (!Double.isNaN(geometry.y())) {
            props.setProperty("y", format(geometry.y()));
        }
        props.setProperty("width", format(geometry.width()));
        props.setProperty("height", format(geometry.height()));
        props.setProperty("divider", format(geometry.divider()));
        props.setProperty("viewMode", geometry.viewMode().name());
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "PINGUI window geometry (P24-006)");
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to save window geometry: " + file, ex);
        }
    }

    private static UiViewMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return UiViewMode.SIMPLE;
        }
        try {
            return UiViewMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UiViewMode.SIMPLE;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return fallback;
            }
            return value;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String format(double value) {
        return Double.toString(value);
    }

    /** True when bounds match within 1px (CHECKLIST restore tolerance). */
    static boolean boundsNearlyEqual(WindowGeometry a, WindowGeometry b) {
        return Math.abs(a.x() - b.x()) <= 1.0
                && Math.abs(a.y() - b.y()) <= 1.0
                && Math.abs(a.width() - b.width()) <= 1.0
                && Math.abs(a.height() - b.height()) <= 1.0;
    }

    Optional<Path> existingFile() {
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }
}
