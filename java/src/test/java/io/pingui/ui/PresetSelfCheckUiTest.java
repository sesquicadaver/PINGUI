package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.probe.PresetSelfCheckResult;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class PresetSelfCheckUiTest {

    @Test
    void formatAlertBodyListsWarnAndOkLines() {
        PresetSelfCheckResult result = new PresetSelfCheckResult(
                List.of(
                        new PresetSelfCheckResult.PresetCheck(
                                "df", "DF", List.of("-4", "-M", "do"), 3, 0, 0.0, OptionalDouble.of(4.2), false),
                        new PresetSelfCheckResult.PresetCheck(
                                "dscp", "DSCP", List.of("-4", "-Q", "46"), 3, 1, 33.333, OptionalDouble.of(9.0), true)),
                true);

        String body = PresetSelfCheckUi.formatAlertBody(result);
        assertTrue(body.contains("✓ DF"));
        assertTrue(body.contains("⚠ DSCP"));
        assertTrue(body.contains("avgRTT=4.20 ms"));
        assertTrue(body.contains("-M do"));
        assertTrue(body.contains("не змінює форму Expert"));
    }

    @Test
    void progressFractionAndStatusLine() {
        PresetSelfCheckUi.Progress mid = new PresetSelfCheckUi.Progress(2, 3, "dscp");
        assertEquals(2.0 / 3.0, mid.fraction(), 1e-9);
        assertEquals("Self-check: dscp (2/3)", mid.statusLine());
        assertEquals(1.0, new PresetSelfCheckUi.Progress(0, 0, "x").fraction(), 1e-9);
    }
}
