package io.pingui.monitor;

import java.util.concurrent.Semaphore;

/** Limits simultaneous TRACE polls (P13-030 / ADR_PROBE_MODES). */
public final class TraceConcurrencyLimiter {
    public static final int DEFAULT_MAX = 3;

    private final Semaphore semaphore;

    public TraceConcurrencyLimiter(int maxConcurrentTraces) {
        if (maxConcurrentTraces < 1) {
            throw new IllegalArgumentException("maxConcurrentTraces must be >= 1");
        }
        this.semaphore = new Semaphore(maxConcurrentTraces);
    }

    public static boolean limitsConcurrency(HostProbeMode mode) {
        return mode == HostProbeMode.TRACE;
    }

    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public void release() {
        semaphore.release();
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
