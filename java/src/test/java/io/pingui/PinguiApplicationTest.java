package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.TracingProfile;
import io.pingui.probe.ProbeMode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PinguiApplicationTest {

    @Test
    void parseOptions_emptyParams_noProfileOverrides() {
        AppOptions options = PinguiApplication.parseOptions(Map.of());
        assertTrue(options.profileOverrides().isEmpty());
    }

    @Test
    void parseOptions_intervalOverrideOnly() {
        AppOptions options = PinguiApplication.parseOptions(Map.of("interval", "2.5"));
        assertEquals(2.5, options.profileOverrides().intervalSeconds().orElseThrow());
        assertTrue(options.profileOverrides().maxHops().isEmpty());
    }

    @Test
    void profileOverrides_mergePreservesYamlFields() {
        TracingProfile yaml = new TracingProfile(30.0, 15, 1.0, ProbeMode.PROCESS, java.util.List.of());
        TracingProfile merged = CliProfileOverrides.none().applyTo(yaml);
        assertEquals(30.0, merged.intervalSeconds());
        assertEquals(15, merged.maxHops());

        CliProfileOverrides partial = new CliProfileOverrides(
                java.util.OptionalDouble.of(2.0),
                java.util.OptionalInt.empty(),
                java.util.OptionalDouble.empty(),
                java.util.Optional.empty());
        TracingProfile after = partial.applyTo(yaml);
        assertEquals(2.0, after.intervalSeconds());
        assertEquals(15, after.maxHops());
        assertEquals(ProbeMode.PROCESS, after.probeMode());
    }
}
