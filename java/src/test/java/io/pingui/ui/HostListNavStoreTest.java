package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostListNavStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripPersistsFilterSortAndProblemsFirst() {
        Path file = tempDir.resolve("host-list-nav.properties");
        HostListNavStore store = new HostListNavStore(file);
        HostListNavPrefs prefs = new HostListNavPrefs("dc", HostListSortMode.SEVERITY, true);
        store.save(prefs);

        HostListNavPrefs loaded = store.load();
        assertEquals("dc", loaded.textFilter());
        assertEquals(HostListSortMode.SEVERITY, loaded.sortMode());
        assertTrue(loaded.problemsFirst());
    }

    @Test
    void missingFileReturnsDefaults() {
        HostListNavPrefs loaded = new HostListNavStore(tempDir.resolve("missing.properties")).load();
        assertEquals("", loaded.textFilter());
        assertEquals(HostListSortMode.CONFIG, loaded.sortMode());
        assertFalse(loaded.problemsFirst());
    }
}
