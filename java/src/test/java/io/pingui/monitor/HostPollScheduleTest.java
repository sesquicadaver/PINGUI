package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class HostPollScheduleTest {

    @Test
    void effectiveIntervalUsesModeDefaults() {
        assertEquals(1.5, HostPollSchedule.effectiveInterval(HostProbeMode.PING_ONLY, 30.0, OptionalDouble.empty()));
        assertEquals(10.0, HostPollSchedule.effectiveInterval(HostProbeMode.MTR, 30.0, OptionalDouble.empty()));
        assertEquals(45.0, HostPollSchedule.effectiveInterval(HostProbeMode.TRACE, 45.0, OptionalDouble.empty()));
    }

    @Test
    void hostOverrideWinsOverModeDefault() {
        assertEquals(7.0, HostPollSchedule.effectiveInterval(HostProbeMode.PING_ONLY, 30.0, OptionalDouble.of(7.0)));
    }

    @Test
    void hostOverrideMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HostPollSchedule.effectiveInterval(HostProbeMode.TRACE, 30.0, OptionalDouble.of(0)));
    }

    @Test
    void isDueRespectsInterval() {
        Instant start = Instant.parse("2026-07-09T10:00:00Z");
        assertTrue(HostPollSchedule.isDue(null, start, 2.0));
        assertFalse(HostPollSchedule.isDue(start, start.plusMillis(500), 2.0));
        assertTrue(HostPollSchedule.isDue(start, start.plusMillis(2000), 2.0));
    }
}
