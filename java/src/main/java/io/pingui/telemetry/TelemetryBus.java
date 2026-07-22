package io.pingui.telemetry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Async telemetry bus: non-blocking offer → bounded queue → batch flush into {@link SinkRegistry}
 * (P16-012 / ADR_TELEMETRY). Optionally feeds {@link AggregateTelemetryJob} for {@code log_aggregates}
 * (P20-009).
 *
 * <p><b>Drop policy:</b> see {@link DropPolicy}. Overflow never blocks the poll loop; discarded
 * items are counted via {@link #droppedCount()}.
 *
 * <p>This class does <em>not</em> close the registry — ownership stays with the daemon.
 */
public final class TelemetryBus implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(TelemetryBus.class.getName());

    public static final int DEFAULT_CAPACITY = 8192;
    public static final int DEFAULT_BATCH_SIZE = 64;
    public static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofMillis(50);

    private final SinkRegistry registry;
    private final AggregateTelemetryJob aggregates;
    private final ArrayBlockingQueue<BusItem> queue;
    private final DropPolicy dropPolicy;
    private final int batchSize;
    private final long flushIntervalNanos;
    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    public TelemetryBus(SinkRegistry registry) {
        this(registry, AggregateTelemetryJob.disabled(registry));
    }

    /** Bus with optional 5m RTT aggregate job (YAML {@code log_aggregates}). */
    public TelemetryBus(SinkRegistry registry, AggregateTelemetryJob aggregates) {
        this(
                registry,
                DEFAULT_CAPACITY,
                DropPolicy.DROP_OLDEST,
                DEFAULT_BATCH_SIZE,
                DEFAULT_FLUSH_INTERVAL,
                aggregates);
    }

    public TelemetryBus(
            SinkRegistry registry, int capacity, DropPolicy dropPolicy, int batchSize, Duration flushInterval) {
        this(registry, capacity, dropPolicy, batchSize, flushInterval, AggregateTelemetryJob.disabled(registry));
    }

    public TelemetryBus(
            SinkRegistry registry,
            int capacity,
            DropPolicy dropPolicy,
            int batchSize,
            Duration flushInterval,
            AggregateTelemetryJob aggregates) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        Objects.requireNonNull(flushInterval, "flushInterval");
        if (flushInterval.isNegative() || flushInterval.isZero()) {
            throw new IllegalArgumentException("flushInterval must be positive");
        }
        this.dropPolicy = Objects.requireNonNull(dropPolicy, "dropPolicy");
        this.batchSize = batchSize;
        this.flushIntervalNanos = flushInterval.toNanos();
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = new Thread(this::runLoop, "pingui-telemetry-bus");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public DropPolicy dropPolicy() {
        return dropPolicy;
    }

    public int capacity() {
        return queue.remainingCapacity() + queue.size();
    }

    public int queued() {
        return queue.size();
    }

    public long droppedCount() {
        return droppedCount.get();
    }

    public SinkRegistry registry() {
        return registry;
    }

    /** Aggregate job wired for this bus (may be disabled). */
    public AggregateTelemetryJob aggregates() {
        return aggregates;
    }

    /**
     * Non-blocking enqueue of a sample. Returns {@code false} only when the item was dropped under
     * {@link DropPolicy#DROP_NEWEST} (or a race after DROP_OLDEST still failed).
     */
    public boolean offerSample(MetricSample sample) {
        Objects.requireNonNull(sample, "sample");
        return offer(BusItem.sample(sample));
    }

    /** Non-blocking enqueue of an event. See {@link #offerSample(MetricSample)}. */
    public boolean offerEvent(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        return offer(BusItem.event(event));
    }

    private boolean offer(BusItem item) {
        if (!running.get()) {
            droppedCount.incrementAndGet();
            return false;
        }
        if (queue.offer(item)) {
            return true;
        }
        if (dropPolicy == DropPolicy.DROP_NEWEST) {
            droppedCount.incrementAndGet();
            return false;
        }
        BusItem discarded = queue.poll();
        if (discarded != null) {
            droppedCount.incrementAndGet();
        }
        if (queue.offer(item)) {
            return true;
        }
        droppedCount.incrementAndGet();
        return false;
    }

    private void runLoop() {
        List<BusItem> batch = new ArrayList<>(batchSize);
        while (running.get() || !queue.isEmpty()) {
            try {
                batch.clear();
                BusItem first = queue.poll(flushIntervalNanos, TimeUnit.NANOSECONDS);
                if (first != null) {
                    batch.add(first);
                }
                queue.drainTo(batch, batchSize - batch.size());
                flushBatch(batch);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    break;
                }
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Telemetry bus flush failed", ex);
            }
        }
        batch.clear();
        queue.drainTo(batch);
        flushBatch(batch);
    }

    private void flushBatch(List<BusItem> batch) {
        for (BusItem item : batch) {
            if (item.sample() != null) {
                registry.emitSample(item.sample());
                aggregates.accept(item.sample());
            } else if (item.event() != null) {
                registry.emitEvent(item.event());
            }
        }
        aggregates.flushDue();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        worker.interrupt();
        try {
            worker.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) {
            LOG.warning("Telemetry bus worker did not stop within timeout");
        }
        // Catch offers that raced past running=false / final worker drain.
        List<BusItem> leftover = new ArrayList<>();
        queue.drainTo(leftover);
        flushBatch(leftover);
        aggregates.flushAll();
    }

    private record BusItem(MetricSample sample, TelemetryEvent event) {
        static BusItem sample(MetricSample sample) {
            return new BusItem(sample, null);
        }

        static BusItem event(TelemetryEvent event) {
            return new BusItem(null, event);
        }
    }
}
