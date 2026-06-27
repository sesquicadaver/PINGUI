package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteChangeDetectorTest {
    @Test
    void noChangeOnFirstObservation() {
        var result = RouteChangeDetector.detect(List.of(), List.of("10.0.0.1"));
        assertFalse(result.changed());
    }

    @Test
    void detectsChange() {
        var result = RouteChangeDetector.detect(List.of("10.0.0.1"), List.of("192.168.1.1"));
        assertTrue(result.changed());
    }

    @Test
    void noChangeWhenEqual() {
        var result = RouteChangeDetector.detect(List.of("10.0.0.1", "8.8.8.8"), List.of("10.0.0.1", "8.8.8.8"));
        assertFalse(result.changed());
    }
}
