package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PingPresetTest {
    @Test
    void statusLineIncludesLabelArgsSummaryExpectCaution() {
        PingPreset preset = new PingPreset(
                "mtu_probe",
                "MTU probe",
                List.of("-M", "probe", "-s", "1472"),
                "один ping",
                "RTT або timeout",
                "не sweep");
        String line = preset.statusLine();
        assertTrue(line.contains("MTU probe"));
        assertTrue(line.contains("-M probe -s 1472"));
        assertTrue(line.contains("один ping"));
        assertTrue(line.contains("Дивись: RTT або timeout"));
        assertTrue(line.contains("не sweep"));
        String tip = preset.tooltipText();
        assertTrue(tip.contains("-M"));
        assertTrue(tip.contains("один ping"));
    }
}
