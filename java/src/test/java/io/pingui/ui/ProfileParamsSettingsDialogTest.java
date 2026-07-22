package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.CliProfileOverrides;
import io.pingui.config.TracingProfile;
import io.pingui.probe.ProbeMode;
import io.pingui.ui.ProfileParamsSettingsDialog.FormInput;
import io.pingui.ui.ProfileParamsSettingsDialog.Result;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ProfileParamsSettingsDialogTest {
    @Test
    void buildResultParsesPollSettingsAndProbe() {
        TracingProfile baseline = TracingProfile.defaults(List.of());
        FormInput form = new FormInput("2.5", "30", "1.5", "process");
        Result result = ProfileParamsSettingsDialog.buildResult(baseline, form, CliProfileOverrides.none());
        assertEquals(2.5, result.intervalSeconds(), 1e-9);
        assertEquals(30, result.maxHops());
        assertEquals(1.5, result.timeoutSeconds(), 1e-9);
        assertEquals(ProbeMode.PROCESS, result.probeMode());
    }

    @Test
    void buildResultRespectsCliLocksOverForm() {
        TracingProfile baseline = TracingProfile.defaults(List.of());
        CliProfileOverrides locks = new CliProfileOverrides(
                OptionalDouble.of(9.0), OptionalInt.of(7), OptionalDouble.of(3.0), Optional.of(ProbeMode.RAW));
        FormInput form = new FormInput("1", "1", "1", "auto");
        Result result = ProfileParamsSettingsDialog.buildResult(baseline, form, locks);
        assertEquals(9.0, result.intervalSeconds(), 1e-9);
        assertEquals(7, result.maxHops());
        assertEquals(3.0, result.timeoutSeconds(), 1e-9);
        assertEquals(ProbeMode.RAW, result.probeMode());
    }

    @Test
    void buildResultRejectsNonPositiveIntervalAndMaxHops() {
        TracingProfile baseline = TracingProfile.defaults(List.of());
        IllegalArgumentException interval = assertThrows(
                IllegalArgumentException.class,
                () -> ProfileParamsSettingsDialog.buildResult(
                        baseline, new FormInput("0", "20", "0.5", "auto"), CliProfileOverrides.none()));
        assertTrue(interval.getMessage().contains("interval"));

        IllegalArgumentException hops = assertThrows(
                IllegalArgumentException.class,
                () -> ProfileParamsSettingsDialog.buildResult(
                        baseline, new FormInput("1", "0", "0.5", "auto"), CliProfileOverrides.none()));
        assertTrue(hops.getMessage().contains("max_hops"));
    }

    @Test
    void withPollSettingsUpdatesTracingProfile() {
        TracingProfile next = TracingProfile.defaults(List.of()).withPollSettings(4.0, 15, 2.0, ProbeMode.PROCESS);
        assertEquals(4.0, next.intervalSeconds(), 1e-9);
        assertEquals(15, next.maxHops());
        assertEquals(2.0, next.timeoutSeconds(), 1e-9);
        assertEquals(ProbeMode.PROCESS, next.probeMode());
    }
}
