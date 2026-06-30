package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class HostPollOutcomeTest {
    @Test
    void errorFactoryPreservesPreviousIps() {
        List<String> previous = List.of("10.0.0.1", "8.8.8.8");
        HostPollOutcome outcome = HostPollOutcome.error(previous, "probe failed");
        assertNull(outcome.snapshot());
        assertEquals("probe failed", outcome.error());
        assertFalse(outcome.routeChanged());
        assertEquals(previous, outcome.oldIps());
        assertEquals(previous, outcome.currentIps());
        assertEquals(List.of(), outcome.newIps());
    }
}
