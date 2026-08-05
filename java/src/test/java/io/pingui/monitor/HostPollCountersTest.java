package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HostPollCountersTest {
    @Test
    void errorPctIsZeroWithoutAttempts() {
        assertEquals(0.0, HostPollCounters.ZERO.errorPct(), 0.001);
    }

    @Test
    void rejectsErrorsAboveAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new HostPollCounters(1, 2));
    }
}
