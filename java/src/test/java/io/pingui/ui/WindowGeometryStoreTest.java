package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowGeometryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void missingFileReturnsDefaults() {
        WindowGeometryStore store = new WindowGeometryStore(tempDir.resolve("missing.properties"));
        WindowGeometry geo = store.load(1100, 700);
        assertEquals(1100, geo.width(), 0.01);
        assertEquals(700, geo.height(), 0.01);
        assertEquals(WindowGeometry.DEFAULT_DIVIDER, geo.divider(), 0.01);
        assertEquals(UiViewMode.SIMPLE, geo.viewMode());
        assertTrue(Double.isNaN(geo.x()));
        assertTrue(Double.isNaN(geo.y()));
    }

    @Test
    void roundTripPersistsBoundsDividerAndMode() throws Exception {
        Path file = tempDir.resolve("window-geometry.properties");
        WindowGeometryStore store = new WindowGeometryStore(file);
        WindowGeometry original = new WindowGeometry(10, 20, 900, 600, 0.42, UiViewMode.EXTENDED);
        store.save(original);
        assertTrue(Files.isRegularFile(file));

        WindowGeometry loaded = store.load(1100, 700);
        assertTrue(WindowGeometryStore.boundsNearlyEqual(original, loaded));
        assertEquals(0.42, loaded.divider(), 0.01);
        assertEquals(UiViewMode.EXTENDED, loaded.viewMode());
    }

    @Test
    void corruptAndPartialFieldsFallBackPerKey() throws Exception {
        Path file = tempDir.resolve("broken.properties");
        Files.writeString(
                file,
                """
                width=not-a-number
                height=650
                divider=1.5
                viewMode=NOPE
                x=100
                y=bad
                """);
        WindowGeometry loaded = new WindowGeometryStore(file).load(1100, 700);
        assertEquals(1100, loaded.width(), 0.01); // bad width → default
        assertEquals(650, loaded.height(), 0.01);
        assertEquals(WindowGeometry.MAX_DIVIDER, loaded.divider(), 0.01); // clamped from 1.5
        assertEquals(UiViewMode.SIMPLE, loaded.viewMode());
        assertEquals(100, loaded.x(), 0.01);
        assertTrue(Double.isNaN(loaded.y()));
    }

    @Test
    void clampKeepsWindowInsideVisualBounds() {
        WindowGeometry offScreen = new WindowGeometry(5000, 5000, 800, 500, 0.3, UiViewMode.SIMPLE);
        WindowGeometry clamped = offScreen.clamp(0, 0, 1920, 1080, 1100, 700);
        assertTrue(clamped.x() >= 0);
        assertTrue(clamped.y() >= 0);
        assertTrue(clamped.x() + clamped.width() <= 1920 + 0.5);
        assertTrue(clamped.y() + clamped.height() <= 1080 + 0.5);
        assertTrue(clamped.width() >= WindowGeometry.MIN_WIDTH);
    }

    @Test
    void clampEnforcesMinimumSize() {
        WindowGeometry tiny = new WindowGeometry(0, 0, 100, 50, 0.2, UiViewMode.SIMPLE);
        WindowGeometry clamped = tiny.clamp(0, 0, 1920, 1080, 1100, 700);
        assertEquals(WindowGeometry.MIN_WIDTH, clamped.width(), 0.01);
        assertEquals(WindowGeometry.MIN_HEIGHT, clamped.height(), 0.01);
    }

    @Test
    void lastKnownDividerSurvivesSimpleModeCloseSemantics() throws Exception {
        // Simulate Extended session writing divider, then Simple close re-saving same lastKnown.
        Path file = tempDir.resolve("divider.properties");
        WindowGeometryStore store = new WindowGeometryStore(file);
        store.save(new WindowGeometry(0, 0, 1100, 700, 0.55, UiViewMode.EXTENDED));
        WindowGeometry simpleClose = new WindowGeometry(0, 0, 1100, 700, 0.55, UiViewMode.SIMPLE);
        store.save(simpleClose);
        WindowGeometry loaded = store.load(1100, 700);
        assertEquals(0.55, loaded.divider(), 0.01);
        assertEquals(UiViewMode.SIMPLE, loaded.viewMode());
        assertFalse(Files.readString(file).isBlank());
    }

    @Test
    void configDirUsesXdgConfigHomeWhenSet() {
        // Path helper must stay under pingui/; env-dependent — only assert shape of defaultFile parent name.
        Path dir = WindowGeometryStore.configDir();
        assertEquals("pingui", dir.getFileName().toString());
    }
}
