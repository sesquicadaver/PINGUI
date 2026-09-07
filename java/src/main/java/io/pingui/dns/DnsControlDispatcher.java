package io.pingui.dns;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Outer DNS-control scheduler: bounded queue, at most one pending observe per host, AbortPolicy
 * overflow (P35-005).
 *
 * <p>Decouples poll workers from {@link DnsControlTracker#observe(String)} without unbounded
 * backlog. Coalesced submits skip redundant work; rejected submits increment drop counters for
 * {@code /ops} / Prometheus / App Status.
 */
public final class DnsControlDispatcher implements AutoCloseable {
    public static final int DEFAULT_QUEUE_CAPACITY = 64;

    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final ConcurrentHashMap<String, Boolean> pendingByHost = new ConcurrentHashMap<>();
    private final AtomicLong rejectedCount = new AtomicLong();
    private final AtomicLong coalescedCount = new AtomicLong();

    public static DnsControlDispatcher createDefault() {
        return new DnsControlDispatcher(DEFAULT_QUEUE_CAPACITY);
    }

    public DnsControlDispatcher(int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1");
        }
        this.queueCapacity = queueCapacity;
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pingui-dns-control-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Schedules {@code task} for {@code host}, or coalesces when a job is already pending/running.
     *
     * @return {@code true} when accepted or coalesced; {@code false} when rejected (queue full)
     */
    public boolean submit(String host, Runnable task) {
        Objects.requireNonNull(task, "task");
        String key = normalizeHost(host);
        if (pendingByHost.putIfAbsent(key, Boolean.TRUE) != null) {
            coalescedCount.incrementAndGet();
            return true;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    pendingByHost.remove(key);
                }
            });
            return true;
        } catch (RejectedExecutionException ex) {
            pendingByHost.remove(key);
            rejectedCount.incrementAndGet();
            return false;
        }
    }

    /** Operator-visible outer-queue counters (P35-005). */
    public DnsOpsStats opsStats() {
        return new DnsOpsStats(
                queueCapacity,
                executor.getQueue().size(),
                pendingByHost.size(),
                rejectedCount.get(),
                rejectedCount.get(),
                coalescedCount.get(),
                0L);
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    public long droppedCount() {
        return rejectedCount.get();
    }

    public long coalescedCount() {
        return coalescedCount.get();
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    int pendingSizeForTests() {
        return pendingByHost.size();
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        return host.strip().toLowerCase();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        pendingByHost.clear();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
