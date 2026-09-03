package io.pingui.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Load/save {@link HostListNavPrefs} under the user config directory (P31-005). */
final class HostListNavStore {
    private static final Logger LOG = Logger.getLogger(HostListNavStore.class.getName());
    private static final String FILE_NAME = "host-list-nav.properties";

    private final Path file;

    HostListNavStore(Path file) {
        this.file = file;
    }

    static HostListNavStore userDefault() {
        return new HostListNavStore(WindowGeometryStore.configDir().resolve(FILE_NAME));
    }

    Path file() {
        return file;
    }

    HostListNavPrefs load() {
        if (!Files.isRegularFile(file)) {
            return HostListNavPrefs.defaults();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to read host-list nav prefs: " + file, ex);
            return HostListNavPrefs.defaults();
        }
        String text = props.getProperty("textFilter", "");
        HostListSortMode sort = HostListSortMode.parse(props.getProperty("sortMode"));
        boolean problemsFirst = Boolean.parseBoolean(props.getProperty("problemsFirst", "false"));
        return new HostListNavPrefs(text, sort, problemsFirst);
    }

    void save(HostListNavPrefs prefs) {
        if (prefs == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty("textFilter", prefs.textFilter());
        props.setProperty("sortMode", prefs.sortMode().name());
        props.setProperty("problemsFirst", Boolean.toString(prefs.problemsFirst()));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "PINGUI host list navigation (P31-005)");
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to save host-list nav prefs: " + file, ex);
        }
    }
}
