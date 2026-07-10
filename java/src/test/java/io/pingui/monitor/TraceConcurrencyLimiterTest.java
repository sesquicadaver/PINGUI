package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraceConcurrencyLimiterTest {

    @Test
    void limitsConcurrentAcquires() {
        TraceConcurrencyLimiter limiter = new TraceConcurrencyLimiter(2);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        limiter.release();
        assertTrue(limiter.tryAcquire());
        limiter.release();
        limiter.release();
    }

    @Test
    void limitsOnlyTraceMode() {
        assertTrue(TraceConcurrencyLimiter.limitsConcurrency(HostProbeMode.TRACE));
        assertFalse(TraceConcurrencyLimiter.limitsConcurrency(HostProbeMode.MTR));
        assertFalse(TraceConcurrencyLimiter.limitsConcurrency(HostProbeMode.PING_ONLY));
    }

    @Test
    void rejectsInvalidMax() {
        assertThrows(IllegalArgumentException.class, () -> new TraceConcurrencyLimiter(0));
    }
}
