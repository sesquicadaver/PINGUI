package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
