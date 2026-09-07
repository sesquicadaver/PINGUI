package io.pingui.dns;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Forward DNS with a bounded executor queue, per-host coalesce, hard timeout, and TTL cache
 * (P32-005 / P34-007).
 *
 * <p>Keeps {@link InetAddress#getAllByName(String)} off the caller thread. Concurrent resolves for
 * the same hostname share one in-flight lookup. Overflow uses AbortPolicy and increments
 * rejected/dropped counters for App Status / API / Prometheus.
 */
public final class BoundedForwardDnsLookup implements ForwardDnsLookup, AutoCloseable {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    public static final int DEFAULT_POOL_SIZE = 2;
    public static final int DEFAULT_QUEUE_CAPACITY = 64;

    private final ForwardDnsLookup delegate;
    private final ThreadPoolExecutor executor;
    private final Duration timeout;
    private final Duration ttl;
    private final Clock clock;
    private final boolean ownsExecutor;
    private final int queueCapacity;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<InetAddress[]>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong rejectedCount = new AtomicLong();
    private final AtomicLong coalescedCount = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();

    public static BoundedForwardDnsLookup systemDefault() {
        return new BoundedForwardDnsLookup(
                DnsControl.systemLookup(),
                newBoundedPool(DEFAULT_POOL_SIZE, DEFAULT_QUEUE_CAPACITY),
                DEFAULT_TIMEOUT,
                DEFAULT_TTL,
                Clock.systemUTC(),
                true,
                DEFAULT_QUEUE_CAPACITY);
    }

    BoundedForwardDnsLookup(
            ForwardDnsLookup delegate,
            ThreadPoolExecutor executor,
            Duration timeout,
            Duration ttl,
            Clock clock,
            boolean ownsExecutor,
            int queueCapacity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeout = requirePositive(timeout, "timeout");
        this.ttl = requirePositive(ttl, "ttl");
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.ownsExecutor = ownsExecutor;
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1");
        }
        this.queueCapacity = queueCapacity;
    }

    /** Test helper with injectable delegate / timing and default queue capacity. */
    public static BoundedForwardDnsLookup forTests(
            ForwardDnsLookup delegate, Duration timeout, Duration ttl, Clock clock) {
        return forTests(delegate, timeout, ttl, clock, DEFAULT_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    }

    /** Test helper with explicit pool/queue sizing (overflow / coalesce tests). */
    public static BoundedForwardDnsLookup forTests(
            ForwardDnsLookup delegate, Duration timeout, Duration ttl, Clock clock, int poolSize, int queueCapacity) {
        return new BoundedForwardDnsLookup(
                delegate,
                newBoundedPool(poolSize, queueCapacity),
                timeout,
                ttl,
                clock != null ? clock : Clock.systemUTC(),
                true,
                queueCapacity);
    }

    @Override
    public InetAddress[] resolve(String hostname) throws Exception {
        Objects.requireNonNull(hostname, "hostname");
        String key = hostname.strip().toLowerCase();
        Instant now = clock.instant();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            if (cached.failure() != null) {
                throw cached.failure();
            }
            return cached.addresses();
        }

        CompletableFuture<InetAddress[]> created = new CompletableFuture<>();
        CompletableFuture<InetAddress[]> shared = inFlight.putIfAbsent(key, created);
        if (shared != null) {
            coalescedCount.incrementAndGet();
            return awaitShared(shared);
        }

        try {
            Future<InetAddress[]> future;
            try {
                future = executor.submit(() -> delegate.resolve(hostname));
            } catch (RejectedExecutionException ex) {
                rejectedCount.incrementAndGet();
                RejectedExecutionException failure =
                        new RejectedExecutionException("DNS queue full for " + hostname, ex);
                created.completeExceptionally(failure);
                throw failure;
            }
            try {
                InetAddress[] addresses = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                InetAddress[] safe = addresses == null ? new InetAddress[0] : addresses;
                cache.put(key, CacheEntry.ok(safe, now.plus(ttl)));
                created.complete(safe);
                return safe;
            } catch (TimeoutException ex) {
                future.cancel(true);
                timeoutCount.incrementAndGet();
                SocketTimeoutException timeoutEx = new SocketTimeoutException("DNS lookup timed out for " + hostname);
                cache.put(key, CacheEntry.fail(timeoutEx, now.plus(ttl)));
                created.completeExceptionally(timeoutEx);
                throw timeoutEx;
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                Exception failure = cause instanceof Exception checked ? checked : new Exception(cause);
                cache.put(key, CacheEntry.fail(failure, now.plus(ttl)));
                created.completeExceptionally(failure);
                throw failure;
            } catch (InterruptedException ex) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                created.completeExceptionally(ex);
                throw ex;
            }
        } finally {
            inFlight.remove(key, created);
        }
    }

    private InetAddress[] awaitShared(CompletableFuture<InetAddress[]> shared) throws Exception {
        try {
            return shared.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            timeoutCount.incrementAndGet();
            throw new SocketTimeoutException("DNS lookup timed out while coalesced");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new Exception(cause);
        }
    }

    /** Snapshot of queue / coalesce / overflow counters for operators (P34-007). */
    public DnsOpsStats opsStats() {
        return new DnsOpsStats(
                queueCapacity,
                executor.getQueue().size(),
                inFlight.size(),
                rejectedCount.get(),
                rejectedCount.get(),
                coalescedCount.get(),
                timeoutCount.get());
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    /** Overflow drops — same as {@link #rejectedCount()} for DNS queue AbortPolicy. */
    public long droppedCount() {
        return rejectedCount.get();
    }

    public long coalescedCount() {
        return coalescedCount.get();
    }

    public long timeoutCount() {
        return timeoutCount.get();
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    int cacheSizeForTests() {
        return cache.size();
    }

    int inFlightSizeForTests() {
        return inFlight.size();
    }

    @Override
    public void close() {
        if (!ownsExecutor) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static ThreadPoolExecutor newBoundedPool(int poolSize, int queueCapacity) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be >= 1");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1");
        }
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pingui-fwd-dns-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private record CacheEntry(InetAddress[] addresses, Exception failure, Instant expiresAt) {
        static CacheEntry ok(InetAddress[] addresses, Instant expiresAt) {
            return new CacheEntry(addresses, null, expiresAt);
        }

        static CacheEntry fail(Exception failure, Instant expiresAt) {
            return new CacheEntry(new InetAddress[0], failure, expiresAt);
        }
    }
}
