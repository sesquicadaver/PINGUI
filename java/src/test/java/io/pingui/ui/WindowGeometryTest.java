package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindowGeometryTest {
    @Test
    void fitSimpleWidthShrinksWhenStageWiderThanContent() {
        assertEquals(580.0, WindowGeometry.fitSimpleWidth(1100.0, 580.0), 0.001);
        assertEquals(600.0, WindowGeometry.fitSimpleWidth(900.0, 600.0), 0.001);
    }

    @Test
    void fitSimpleWidthKeepsStageWhenAlreadyFitOrContentUnknown() {
        assertEquals(580.0, WindowGeometry.fitSimpleWidth(580.0, 580.0), 0.001);
        assertEquals(600.0, WindowGeometry.fitSimpleWidth(600.0, 650.0), 0.001);
        assertEquals(1100.0, WindowGeometry.fitSimpleWidth(1100.0, 0.0), 0.001);
        assertEquals(1100.0, WindowGeometry.fitSimpleWidth(1100.0, Double.NaN), 0.001);
    }

    @Test
    void fitSimpleWidthRespectsMinimum() {
        assertEquals(WindowGeometry.MIN_WIDTH, WindowGeometry.fitSimpleWidth(1100.0, 400.0), 0.001);
    }

    @Test
    void fitSimpleHeightShrinksWhenStageTallerThanContent() {
        assertEquals(700.0, WindowGeometry.fitSimpleHeight(820.0, 700.0), 0.001);
        assertEquals(500.0, WindowGeometry.fitSimpleHeight(900.0, 500.0), 0.001);
    }

    @Test
    void fitSimpleHeightKeepsStageWhenAlreadyFitOrContentUnknown() {
        assertEquals(700.0, WindowGeometry.fitSimpleHeight(700.0, 700.0), 0.001);
        assertEquals(700.0, WindowGeometry.fitSimpleHeight(700.0, 800.0), 0.001);
        assertEquals(820.0, WindowGeometry.fitSimpleHeight(820.0, 0.0), 0.001);
        assertEquals(WindowGeometry.MIN_HEIGHT, WindowGeometry.fitSimpleHeight(820.0, 200.0), 0.001);
    }

    @Test
    void ensureExtendedWidthExpandsNarrowStageOnly() {
        assertEquals(1400.0, WindowGeometry.ensureExtendedWidth(580.0, 1400.0), 0.001);
        assertEquals(1500.0, WindowGeometry.ensureExtendedWidth(1500.0, 1400.0), 0.001);
        assertEquals(900.0, WindowGeometry.ensureExtendedWidth(900.0, Double.NaN), 0.001);
    }

    @Test
    void ensureExtendedHeightExpandsShortStageOnly() {
        assertEquals(820.0, WindowGeometry.ensureExtendedHeight(700.0, 820.0), 0.001);
        assertEquals(900.0, WindowGeometry.ensureExtendedHeight(900.0, 820.0), 0.001);
        assertEquals(700.0, WindowGeometry.ensureExtendedHeight(700.0, Double.NaN), 0.001);
    }

    @Test
    void dividerForLeftWidthTargetsHostColumn() {
        assertEquals(600.0 / 1400.0, WindowGeometry.dividerForLeftWidth(1400.0, 600.0), 0.001);
        assertEquals(WindowGeometry.DEFAULT_DIVIDER, WindowGeometry.dividerForLeftWidth(0.0, 600.0), 0.001);
        assertEquals(WindowGeometry.MAX_DIVIDER, WindowGeometry.dividerForLeftWidth(580.0, 600.0), 0.001);
    }

    @Test
    void fillsVisualBoundsDetectsMaximizedLeftover() {
        assertTrue(WindowGeometry.fillsVisualBounds(1680, 1025, 1680, 1025));
        assertTrue(WindowGeometry.fillsVisualBounds(1679, 1024, 1680, 1025));
        assertFalse(WindowGeometry.fillsVisualBounds(580, 700, 1680, 1025));
        assertFalse(WindowGeometry.fillsVisualBounds(1400, 820, 1680, 1025));
    }
}
