package io.pingui.persistence;

import io.pingui.model.Models.HostSessionData;
import io.pingui.persistence.timeseries.PingSample;
import io.pingui.persistence.timeseries.RouteEvent;
import io.pingui.persistence.timeseries.TimeSeriesBackend;
import io.pingui.telemetry.DropPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-threaded bounded writer for SQLite session saves and time-series I/O (P33-003).
 *
 * <p>Callers enqueue immutable deltas without blocking on JDBC/HTTP. Overflow increments {@link
 * #droppedCount()} under {@link DropPolicy} (default {@link DropPolicy#DROP_OLDEST}).
 */
public final class SessionPersistenceWriter implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SessionPersistenceWriter.class);

    public static final int DEFAULT_CAPACITY = 256;

    private final ArrayBlockingQueue<Job> queue;
    private final DropPolicy dropPolicy;
    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicLong completedCount = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<SessionDatabase> database = new AtomicReference<>();
    private final AtomicReference<TimeSeriesBackend> timeseries = new AtomicReference<>();
    private final Thread worker;

    public SessionPersistenceWriter(SessionDatabase database, TimeSeriesBackend timeseries) {
        this(DEFAULT_CAPACITY, DropPolicy.DROP_OLDEST, database, timeseries);
    }

    public SessionPersistenceWriter(
            int capacity, DropPolicy dropPolicy, SessionDatabase database, TimeSeriesBackend timeseries) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.dropPolicy = Objects.requireNonNull(dropPolicy, "dropPolicy");
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.database.set(database);
        this.timeseries.set(timeseries);
        this.worker = new Thread(this::runLoop, "pingui-session-persist");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void setDatabase(SessionDatabase database) {
        this.database.set(database);
    }

    public void setTimeSeriesBackend(TimeSeriesBackend timeseries) {
        this.timeseries.set(timeseries);
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

    /** Jobs successfully applied by the worker (test / metrics hook). */
    public long completedCount() {
        return completedCount.get();
    }

    /** Enqueues an immutable host snapshot for SQLite {@code save}. */
    public boolean offerSave(String host, HostSessionData snapshot) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(snapshot, "snapshot");
        return offer(new SaveHost(host, snapshot));
    }

    public boolean offerDelete(String host) {
        Objects.requireNonNull(host, "host");
        return offer(new DeleteHost(host));
    }

    public boolean offerRename(String oldHost, String newHost) {
        Objects.requireNonNull(oldHost, "oldHost");
        Objects.requireNonNull(newHost, "newHost");
        return offer(new RenameHost(oldHost, newHost));
    }

    public boolean offerPingSamples(List<PingSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return true;
        }
        return offer(new WritePings(List.copyOf(samples)));
    }

    public boolean offerRouteEvent(RouteEvent event) {
        Objects.requireNonNull(event, "event");
        return offer(new WriteRoute(event));
    }

    /**
     * Blocks until queued work drained or timeout. Used by {@link #close()} and tests that reopen the
     * DB immediately.
     */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        CountDownLatch done = new CountDownLatch(1);
        if (!offer(new Barrier(done))) {
            return false;
        }
        return done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean offer(Job job) {
        if (!running.get()) {
            droppedCount.incrementAndGet();
            return false;
        }
        if (queue.offer(job)) {
            return true;
        }
        if (dropPolicy == DropPolicy.DROP_NEWEST) {
            droppedCount.incrementAndGet();
            return false;
        }
        Job discarded = queue.poll();
        if (discarded != null) {
            droppedCount.incrementAndGet();
            if (discarded instanceof Barrier barrier) {
                barrier.done().countDown();
            }
        }
        if (queue.offer(job)) {
            return true;
        }
        droppedCount.incrementAndGet();
        if (job instanceof Barrier barrier) {
            barrier.done().countDown();
        }
        return false;
    }

    private void runLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                Job job = queue.poll(50, TimeUnit.MILLISECONDS);
                if (job != null) {
                    apply(job);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    break;
                }
            } catch (RuntimeException ex) {
                LOG.warn("Session persistence worker failed: {}", ex.getMessage());
            }
        }
        List<Job> leftover = new ArrayList<>();
        queue.drainTo(leftover);
        for (Job job : leftover) {
            try {
                apply(job);
            } catch (RuntimeException ex) {
                LOG.warn("Session persistence drain failed: {}", ex.getMessage());
            }
        }
    }

    private void apply(Job job) {
        if (job instanceof SaveHost save) {
            SessionDatabase db = database.get();
            if (db != null) {
                db.save(save.host(), save.snapshot());
            }
            completedCount.incrementAndGet();
        } else if (job instanceof DeleteHost delete) {
            SessionDatabase db = database.get();
            if (db != null) {
                db.delete(delete.host());
            }
            completedCount.incrementAndGet();
        } else if (job instanceof RenameHost rename) {
            SessionDatabase db = database.get();
            if (db != null) {
                db.rename(rename.oldHost(), rename.newHost());
            }
            completedCount.incrementAndGet();
        } else if (job instanceof WritePings pings) {
            TimeSeriesBackend backend = timeseries.get();
            if (backend != null) {
                backend.writePingSamples(pings.samples());
            }
            completedCount.incrementAndGet();
        } else if (job instanceof WriteRoute route) {
            TimeSeriesBackend backend = timeseries.get();
            if (backend != null) {
                backend.writeRouteEvent(route.event());
            }
            completedCount.incrementAndGet();
        } else if (job instanceof Barrier barrier) {
            barrier.done().countDown();
            completedCount.incrementAndGet();
        }
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
            LOG.warn("Session persistence worker did not stop within timeout");
        }
        List<Job> leftover = new ArrayList<>();
        queue.drainTo(leftover);
        for (Job job : leftover) {
            try {
                apply(job);
            } catch (RuntimeException ex) {
                LOG.warn("Session persistence final drain failed: {}", ex.getMessage());
            }
        }
    }

    private sealed interface Job permits SaveHost, DeleteHost, RenameHost, WritePings, WriteRoute, Barrier {}

    private record SaveHost(String host, HostSessionData snapshot) implements Job {}

    private record DeleteHost(String host) implements Job {}

    private record RenameHost(String oldHost, String newHost) implements Job {}

    private record WritePings(List<PingSample> samples) implements Job {}

    private record WriteRoute(RouteEvent event) implements Job {}

    private record Barrier(CountDownLatch done) implements Job {}
}
