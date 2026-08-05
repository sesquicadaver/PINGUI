package io.pingui.monitor;

import java.util.concurrent.Semaphore;

/**
 * Limits simultaneous TRACE polls (P13-030 / ADR_PROBE_MODES).
 *
 * <p>Default equals the session host cap ({@code HostsConfig.MAX_HOSTS} = 10): if the operator
 * enables TRACE on N hosts (N ≤ 10), all of them may run concurrent traces. Lower the YAML
 * {@code max_concurrent_traces} only when deliberately throttling load.
 */
public final class TraceConcurrencyLimiter {
    /** Must stay equal to {@code io.pingui.config.HostsConfig.MAX_HOSTS} (enforced by unit test). */
    public static final int DEFAULT_MAX = 10;

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
