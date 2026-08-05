package io.pingui.i18n;

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

/** Persist selected UI locale under the user config directory (XDG / APPDATA). */
public final class UiLocaleStore {
    private static final Logger LOG = Logger.getLogger(UiLocaleStore.class.getName());
    private static final String FILE_NAME = "ui-locale.properties";

    private final Path file;

    UiLocaleStore(Path file) {
        this.file = file;
    }

    public static UiLocaleStore userDefault() {
        return new UiLocaleStore(defaultFile());
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

    public Optional<UiLocale> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to read UI locale: " + file, ex);
            return Optional.empty();
        }
        return UiLocale.fromCode(props.getProperty("locale"));
    }

    public void save(UiLocale locale) {
        if (locale == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty("locale", locale.code());
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "PINGUI UI locale (P25)");
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to save UI locale: " + file, ex);
        }
    }
}
