package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.PingExpertEntry;
import io.pingui.config.PingPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PresetSelfCheckTest {

    @BeforeEach
    void reloadPresets() {
        PingPresets.configureDefault();
    }

    @Test
    void runsThreePresetsWithConfiguredProbeCount() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ExpertPingOnce ping = (target, expert, timeout) -> {
            calls.incrementAndGet();
            assertTrue(expert.args().contains("-4") || expert.args().contains("-6"));
            return OptionalDouble.of(12.5);
        };
        PresetSelfCheckConfig config = new PresetSelfCheckConfig(2, 0.5, 1.0, false, List.of("df", "dscp", "burst"));
        PresetSelfCheckResult result = new PresetSelfCheck(ping).run("8.8.8.8", config);

        assertEquals(6, calls.get());
        assertEquals(3, result.checks().size());
        assertFalse(result.anyWarn());
        assertEquals("df", result.checks().get(0).presetId());
        assertTrue(result.checks().get(0).extendedArgs().contains("-M"));
        assertTrue(result.checks().get(1).extendedArgs().contains("-Q"));
        assertTrue(result.checks().get(2).extendedArgs().contains("-O"));
        assertEquals(12.5, result.checks().get(0).avgRttMs().orElseThrow(), 1e-9);
    }

    @Test
    void lossAtOrAboveThresholdMarksWarn() throws Exception {
        ExpertPingOnce ping = (target, expert, timeout) -> OptionalDouble.empty();
        PresetSelfCheckConfig config = new PresetSelfCheckConfig(1, 0.5, 1.0, false, List.of("df"));
        PresetSelfCheckResult result = new PresetSelfCheck(ping).run("h", config);

        assertTrue(result.anyWarn());
        assertTrue(result.checks().get(0).warn());
        assertEquals(100.0, result.checks().get(0).lossPct(), 1e-9);
        assertTrue(result.checks().get(0).avgRttMs().isEmpty());
    }

    @Test
    void ipv6UsesMinusSixAndRejectsBlank() throws Exception {
        List<PingExpertEntry> seen = new ArrayList<>();
        ExpertPingOnce ping = (target, expert, timeout) -> {
            seen.add(expert);
            return OptionalDouble.of(1.0);
        };
        new PresetSelfCheck(ping).run("2001:db8::1", new PresetSelfCheckConfig(1, 0.5, 50.0, true, List.of("df")));
        assertTrue(seen.get(0).args().contains("-6"));
        assertThrows(IllegalArgumentException.class, () -> new PresetSelfCheck(ping)
                .run("  ", PresetSelfCheckConfig.ipv4Defaults()));
        assertThrows(IllegalArgumentException.class, () -> new PresetSelfCheck(ping)
                .run("h", new PresetSelfCheckConfig(1, 0.5, 1.0, false, List.of("nope"))));
    }
}
