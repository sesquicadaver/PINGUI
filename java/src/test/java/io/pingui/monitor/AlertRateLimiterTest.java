package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AlertRateLimiterTest {
    @Test
    void allowsUpToMaxPerHour() {
        AlertRateLimiter limiter = new AlertRateLimiter(3);
        double base = 1_700_000_000.0;
        assertTrue(limiter.allow("8.8.8.8", base));
        assertTrue(limiter.allow("8.8.8.8", base + 1));
        assertTrue(limiter.allow("8.8.8.8", base + 2));
        assertFalse(limiter.allow("8.8.8.8", base + 3));
    }

    @Test
    void separateHostsHaveIndependentLimits() {
        AlertRateLimiter limiter = new AlertRateLimiter(1);
        double base = 1_700_000_000.0;
        assertTrue(limiter.allow("8.8.8.8", base));
        assertFalse(limiter.allow("8.8.8.8", base + 1));
        assertTrue(limiter.allow("1.1.1.1", base + 1));
    }

    @Test
    void dropsEntriesOutsideRollingWindow() {
        AlertRateLimiter limiter = new AlertRateLimiter(1);
        double base = 1_700_000_000.0;
        assertTrue(limiter.allow("8.8.8.8", base));
        assertFalse(limiter.allow("8.8.8.8", base + 10));
        assertTrue(limiter.allow("8.8.8.8", base + 3601));
    }
}
