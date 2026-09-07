package io.pingui.persistence;

import io.pingui.model.Models.HostSessionData;
import io.pingui.persistence.timeseries.PingSample;
import io.pingui.persistence.timeseries.RouteEvent;
import io.pingui.persistence.timeseries.TimeSeriesBackend;
import io.pingui.telemetry.DropPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-lane session persistence writer (P33-003 / P34-004 / P35-007 / P35-008).
 *
 * <ul>
 *   <li><b>Control lane</b> (unbounded): delete, rename, barrier, poll history — never dropped on
 *       overflow.
 *   <li><b>State lane</b>: coalesced {@code SaveHost} (latest snapshot per host).
 *   <li><b>Telemetry lane</b> (bounded): ping/route samples may drop under {@link DropPolicy}.
 * </ul>
 *
 * <p>{@link #close()} joins the worker until it stops so callers can safely close the DB afterward.
 * If the worker is still alive after the join budget, {@code close} skips caller-side
 * {@code drainRemaining} so a stuck apply cannot race parallel JDBC (P35-008). Poll history
 * ({@link #offerPollHistory}) runs inside {@link SessionDatabase#inTransaction} via an injected
 * applier so probe threads never block on JDBC for events+poll+route.
 */
public final class SessionPersistenceWriter implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SessionPersistenceWriter.class);

    public static final int DEFAULT_CAPACITY = 256;
    /** Max wait for the worker to stop so SessionStore can close SQLite safely (P34-004). */
    static final Duration CLOSE_JOIN_TIMEOUT = Duration.ofSeconds(30);
    /** Extra grace after the primary join before deciding the worker is stuck (P35-008). */
    static final Duration CLOSE_STUCK_GRACE = Duration.ofSeconds(5);

    private final LinkedBlockingQueue<Job> controlQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, HostSessionData> pendingSaves = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<String> dirtyHosts = new LinkedBlockingQueue<>();
    private final Set<String> dirtySet = ConcurrentHashMap.newKeySet();
    private final ArrayBlockingQueue<Job> telemetryQueue;
    private final DropPolicy telemetryDropPolicy;
    private final Duration closeJoinTimeout;
    private final Duration closeStuckGrace;
    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicLong completedCount = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<SessionDatabase> database = new AtomicReference<>();
    private final AtomicReference<TimeSeriesBackend> timeseries = new AtomicReference<>();
    private final AtomicReference<Consumer<PollPersistenceBatch>> pollHistoryApplier = new AtomicReference<>();
    private final Thread worker;

    public SessionPersistenceWriter(SessionDatabase database, TimeSeriesBackend timeseries) {
        this(DEFAULT_CAPACITY, DropPolicy.DROP_OLDEST, database, timeseries);
    }

    public SessionPersistenceWriter(
            int telemetryCapacity, DropPolicy dropPolicy, SessionDatabase database, TimeSeriesBackend timeseries) {
        this(CLOSE_JOIN_TIMEOUT, CLOSE_STUCK_GRACE, telemetryCapacity, dropPolicy, database, timeseries);
    }

    /**
     * Package-visible constructor for fault tests that need a short close join (P35-008).
     *
     * @param closeJoinTimeout primary join budget before treating the worker as stuck
     * @param closeStuckGrace brief extra join before skipping caller drain
     */
    SessionPersistenceWriter(
            Duration closeJoinTimeout,
            Duration closeStuckGrace,
            int telemetryCapacity,
            DropPolicy dropPolicy,
            SessionDatabase database,
            TimeSeriesBackend timeseries) {
        if (telemetryCapacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.closeJoinTimeout = Objects.requireNonNull(closeJoinTimeout, "closeJoinTimeout");
        this.closeStuckGrace = Objects.requireNonNull(closeStuckGrace, "closeStuckGrace");
        if (closeJoinTimeout.isNegative() || closeJoinTimeout.isZero()) {
            throw new IllegalArgumentException("closeJoinTimeout must be > 0");
        }
        if (closeStuckGrace.isNegative()) {
            throw new IllegalArgumentException("closeStuckGrace must be >= 0");
        }
        this.telemetryDropPolicy = Objects.requireNonNull(dropPolicy, "dropPolicy");
        this.telemetryQueue = new ArrayBlockingQueue<>(telemetryCapacity);
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

    /**
     * Applies {@link PollPersistenceBatch} on the worker thread inside one DB transaction (P35-007).
     * Typically wired to {@code PollResultEffects} sync JDBC helpers.
     */
    public void setPollHistoryApplier(Consumer<PollPersistenceBatch> applier) {
        this.pollHistoryApplier.set(applier);
    }

    public DropPolicy dropPolicy() {
        return telemetryDropPolicy;
    }

    /** Telemetry queue capacity (control lane is unbounded). */
    public int capacity() {
        return telemetryQueue.remainingCapacity() + telemetryQueue.size();
    }

    public int queued() {
        return controlQueue.size() + dirtyHosts.size() + telemetryQueue.size();
    }

    /** Telemetry (lossy) drops only — control jobs never increment this (P34-004). */
    public long droppedCount() {
        return droppedCount.get();
    }

    /** Jobs successfully applied by the worker (test / metrics hook). */
    public long completedCount() {
        return completedCount.get();
    }

    /** Enqueues an immutable host snapshot; coalesced per host (latest wins). */
    public boolean offerSave(String host, HostSessionData snapshot) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!running.get()) {
            return false;
        }
        pendingSaves.put(host, snapshot);
        if (dirtySet.add(host)) {
            dirtyHosts.offer(host);
        }
        return true;
    }

    public boolean offerDelete(String host) {
        Objects.requireNonNull(host, "host");
        // Drop any coalesced save that would resurrect the host after delete.
        pendingSaves.remove(host);
        dirtySet.remove(host);
        return offerControl(new DeleteHost(host));
    }

    public boolean offerRename(String oldHost, String newHost) {
        Objects.requireNonNull(oldHost, "oldHost");
        Objects.requireNonNull(newHost, "newHost");
        HostSessionData pending = pendingSaves.remove(oldHost);
        dirtySet.remove(oldHost);
        if (pending != null) {
            pendingSaves.put(newHost, pending);
            if (dirtySet.add(newHost)) {
                dirtyHosts.offer(newHost);
            }
        }
        return offerControl(new RenameHost(oldHost, newHost));
    }

    public boolean offerPingSamples(List<PingSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return true;
        }
        return offerTelemetry(new WritePings(List.copyOf(samples)));
    }

    public boolean offerRouteEvent(RouteEvent event) {
        Objects.requireNonNull(event, "event");
        return offerTelemetry(new WriteRoute(event));
    }

    /**
     * Enqueues CompletedPoll history (events + poll_result + route) on the control lane (P35-007).
     * Never dropped; applied in one SQLite transaction.
     */
    public boolean offerPollHistory(PollPersistenceBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return offerControl(new WritePollHistory(batch));
    }

    /**
     * Enqueues arbitrary JDBC work on the control lane inside one transaction (route_change, quality,
     * dns_change, …) so probe / DNS dispatcher threads stay non-blocking (P35-007).
     */
    public boolean offerJdbc(Runnable work) {
        Objects.requireNonNull(work, "work");
        return offerControl(new WriteJdbc(work));
    }

    /**
     * Blocks until queued work drained or timeout. Used by {@link #close()} and tests that reopen the
     * DB immediately.
     */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        CountDownLatch done = new CountDownLatch(1);
        if (!offerControl(new Barrier(done))) {
            return false;
        }
        return done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean offerControl(Job job) {
        if (!running.get()) {
            droppedCount.incrementAndGet();
            return false;
        }
        // Unbounded control lane — never drop structural jobs (P34-004).
        controlQueue.offer(job);
        return true;
    }

    private boolean offerTelemetry(Job job) {
        if (!running.get()) {
            droppedCount.incrementAndGet();
            return false;
        }
        if (telemetryQueue.offer(job)) {
            return true;
        }
        if (telemetryDropPolicy == DropPolicy.DROP_NEWEST) {
            droppedCount.incrementAndGet();
            return false;
        }
        Job discarded = telemetryQueue.poll();
        if (discarded != null) {
            droppedCount.incrementAndGet();
        }
        if (telemetryQueue.offer(job)) {
            return true;
        }
        droppedCount.incrementAndGet();
        return false;
    }

    private boolean hasPendingWork() {
        return !controlQueue.isEmpty() || hasSiblingLaneWork();
    }

    /** State + telemetry lanes that must drain before a {@link Barrier} may complete. */
    private boolean hasSiblingLaneWork() {
        return !dirtyHosts.isEmpty() || !pendingSaves.isEmpty() || !telemetryQueue.isEmpty();
    }

    private void runLoop() {
        while (running.get() || hasPendingWork()) {
            try {
                Job job = nextJob(50);
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
        drainRemaining();
    }

    /**
     * Picks the next job. A {@link Barrier} must never be returned until coalesced saves and
     * telemetry are empty — including when a timed {@code controlQueue.poll} wakes on a barrier
     * (that path previously completed awaitIdle early and dropped pending work).
     */
    private Job nextJob(long waitMs) throws InterruptedException {
        while (true) {
            Job head = controlQueue.peek();
            if (head != null && !(head instanceof Barrier)) {
                return controlQueue.poll();
            }

            Job save = pollCoalescedSave();
            if (save != null) {
                return save;
            }
            Job telemetry = telemetryQueue.poll();
            if (telemetry != null) {
                return telemetry;
            }

            if (head instanceof Barrier) {
                if (hasSiblingLaneWork()) {
                    // Work raced in after the polls above; retry without completing the barrier.
                    continue;
                }
                Job polled = controlQueue.poll();
                if (polled != null) {
                    return polled;
                }
                continue;
            }

            Job polled = controlQueue.poll(waitMs, TimeUnit.MILLISECONDS);
            if (polled == null) {
                return null;
            }
            if (polled instanceof Barrier) {
                // Timed wait dequeued the barrier directly — put it back and drain siblings first.
                controlQueue.offer(polled);
                continue;
            }
            return polled;
        }
    }

    private Job pollCoalescedSave() {
        String host = dirtyHosts.poll();
        if (host != null) {
            dirtySet.remove(host);
            HostSessionData snapshot = pendingSaves.remove(host);
            if (snapshot != null) {
                return new SaveHost(host, snapshot);
            }
            // Stale dirty marker (e.g. after delete); try any remaining pending snapshot.
        }
        // Recover orphaned pending snapshots so barriers cannot spin forever.
        for (String pendingHost : pendingSaves.keySet()) {
            HostSessionData snapshot = pendingSaves.remove(pendingHost);
            if (snapshot != null) {
                dirtySet.remove(pendingHost);
                return new SaveHost(pendingHost, snapshot);
            }
        }
        return null;
    }

    private void drainRemaining() {
        List<Job> leftover = new ArrayList<>();
        controlQueue.drainTo(leftover);
        for (Job job : leftover) {
            if (job instanceof Barrier barrier) {
                // Finish pending work before signaling waiters during shutdown drain.
                flushPendingSavesInline();
                Job tel;
                while ((tel = telemetryQueue.poll()) != null) {
                    safeApply(tel);
                }
                barrier.done().countDown();
                completedCount.incrementAndGet();
            } else {
                safeApply(job);
            }
        }
        flushPendingSavesInline();
        leftover.clear();
        telemetryQueue.drainTo(leftover);
        for (Job job : leftover) {
            safeApply(job);
        }
    }

    /** Applies coalesced saves currently marked dirty. */
    private void flushPendingSavesInline() {
        Set<String> hosts = new HashSet<>();
        dirtyHosts.drainTo(hosts);
        hosts.addAll(dirtySet);
        hosts.addAll(pendingSaves.keySet());
        dirtySet.clear();
        for (String host : hosts) {
            HostSessionData snapshot = pendingSaves.remove(host);
            if (snapshot != null) {
                safeApply(new SaveHost(host, snapshot));
            }
        }
    }

    private void safeApply(Job job) {
        try {
            apply(job);
        } catch (RuntimeException ex) {
            LOG.warn("Session persistence drain failed: {}", ex.getMessage());
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
        } else if (job instanceof WritePollHistory history) {
            SessionDatabase db = database.get();
            Consumer<PollPersistenceBatch> applier = pollHistoryApplier.get();
            if (db != null && applier != null) {
                db.inTransaction(() -> {
                    applier.accept(history.batch());
                    return null;
                });
            } else if (applier != null) {
                applier.accept(history.batch());
            } else {
                LOG.warn(
                        "Poll history dropped: no applier configured for {}",
                        history.batch().poll().host());
            }
            completedCount.incrementAndGet();
        } else if (job instanceof WriteJdbc jdbc) {
            SessionDatabase db = database.get();
            if (db != null) {
                db.inTransaction(() -> {
                    jdbc.work().run();
                    return null;
                });
            } else {
                jdbc.work().run();
            }
            completedCount.incrementAndGet();
        } else if (job instanceof Barrier barrier) {
            if (hasSiblingLaneWork()) {
                // Safety net: never release awaitIdle while sibling lanes still have work.
                controlQueue.offer(barrier);
                return;
            }
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
        long deadlineNs = System.nanoTime() + closeJoinTimeout.toNanos();
        try {
            while (worker.isAlive() && System.nanoTime() < deadlineNs) {
                long remainingMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()));
                worker.join(Math.min(500L, remainingMs));
                if (worker.isAlive()) {
                    worker.interrupt();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        // Brief grace for an in-flight apply to finish before deciding the worker is stuck (P35-008).
        if (worker.isAlive() && !closeStuckGrace.isZero()) {
            try {
                worker.join(closeStuckGrace.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (worker.isAlive()) {
            // Caller must NOT drain while the worker still owns apply() — parallel JDBC races the DB
            // and can double-apply control jobs (P35-008). Prefer leaving leftovers queued.
            LOG.error(
                    "Session persistence worker did not stop within {}; skipping caller drain while worker alive",
                    closeJoinTimeout.plus(closeStuckGrace));
            return;
        }
        // Worker exited: finish any leftovers the loop did not flush (normal shutdown path).
        drainRemaining();
    }

    /** Test hook: true when the background worker has terminated. */
    boolean workerAliveForTests() {
        return worker.isAlive();
    }

    private sealed interface Job
            permits SaveHost, DeleteHost, RenameHost, WritePings, WriteRoute, WritePollHistory, WriteJdbc, Barrier {}

    private record SaveHost(String host, HostSessionData snapshot) implements Job {}

    private record DeleteHost(String host) implements Job {}

    private record RenameHost(String oldHost, String newHost) implements Job {}

    private record WritePings(List<PingSample> samples) implements Job {}

    private record WriteRoute(RouteEvent event) implements Job {}

    private record WritePollHistory(PollPersistenceBatch batch) implements Job {}

    private record WriteJdbc(Runnable work) implements Job {}

    private record Barrier(CountDownLatch done) implements Job {}
}
