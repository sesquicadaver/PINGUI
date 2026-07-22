package io.pingui.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Apply per-host rate limiting before delegating to an inner dispatcher (P10-040). */
public final class RateLimitedAlertDispatcher implements AlertDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitedAlertDispatcher.class);

    private final AlertDispatcher inner;
    private final AlertRateLimiter limiter;

    public RateLimitedAlertDispatcher(AlertDispatcher inner, AlertRateLimiter limiter) {
        if (inner == null) {
            throw new IllegalArgumentException("inner dispatcher is required");
        }
        if (limiter == null) {
            throw new IllegalArgumentException("limiter is required");
        }
        this.inner = inner;
        this.limiter = limiter;
    }

    @Override
    public void dispatch(RouteChangeEvent event) {
        if (!limiter.allow(event.host())) {
            LOG.debug("Alert rate-limited for host {}", event.host());
            return;
        }
        inner.dispatch(event);
    }

    @Override
    public void dispatchQuality(QualityAlertEvent event) {
        if (event == null) {
            return;
        }
        if (!limiter.allow(event.host())) {
            LOG.debug("Quality alert rate-limited for host {}", event.host());
            return;
        }
        inner.dispatchQuality(event);
    }
}
