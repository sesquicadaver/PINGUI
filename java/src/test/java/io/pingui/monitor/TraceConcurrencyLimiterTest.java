package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostsConfig;
import org.junit.jupiter.api.Test;

class TraceConcurrencyLimiterTest {

    @Test
    void defaultMaxMatchesSessionHostCap() {
        assertEquals(HostsConfig.MAX_HOSTS, TraceConcurrencyLimiter.DEFAULT_MAX);
    }

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
    void defaultAllowsFullSessionOfTraces() {
        TraceConcurrencyLimiter limiter = new TraceConcurrencyLimiter(TraceConcurrencyLimiter.DEFAULT_MAX);
        for (int i = 0; i < HostsConfig.MAX_HOSTS; i++) {
            assertTrue(limiter.tryAcquire(), "slot " + i);
        }
        assertFalse(limiter.tryAcquire());
        limiter.release();
        assertTrue(limiter.tryAcquire());
        for (int i = 0; i < HostsConfig.MAX_HOSTS; i++) {
            limiter.release();
        }
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
