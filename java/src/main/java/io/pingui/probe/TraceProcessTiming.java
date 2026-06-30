package io.pingui.probe;

/** Subprocess wait budgets for traceroute/tracert. */
final class TraceProcessTiming {

    private TraceProcessTiming() {}

    /** Max wait for the trace subprocess (tracert on Windows needs much longer than 0.5s * hops). */
    static long computeProcessWaitMs(boolean windows, int maxHops, double timeoutSeconds) {
        if (windows) {
            int perReplyMs = windowsTracertWaitMs(timeoutSeconds);
            return (long) maxHops * 3L * perReplyMs + 15_000L;
        }
        return (long) (timeoutSeconds * 1000 * (maxHops + 2) + 5000);
    }

    /** Per-reply timeout for {@code tracert -w} (Windows default is 4000 ms). */
    static int windowsTracertWaitMs(double timeoutSeconds) {
        return Math.max(4000, (int) Math.ceil(timeoutSeconds * 1000));
    }
}
