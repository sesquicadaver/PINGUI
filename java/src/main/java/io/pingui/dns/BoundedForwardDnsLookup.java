package io.pingui.dns;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forward DNS with a bounded executor, hard timeout, and positive/negative TTL cache (P32-005).
 *
 * <p>Keeps {@link InetAddress#getAllByName(String)} off the caller thread so probe/UI loops cannot
 * hang indefinitely on a stuck resolver.
 */
public final class BoundedForwardDnsLookup implements ForwardDnsLookup, AutoCloseable {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final ForwardDnsLookup delegate;
    private final ExecutorService executor;
    private final Duration timeout;
    private final Duration ttl;
    private final Clock clock;
    private final boolean ownsExecutor;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public static BoundedForwardDnsLookup systemDefault() {
        return new BoundedForwardDnsLookup(
                DnsControl.systemLookup(), newBoundedPool(2), DEFAULT_TIMEOUT, DEFAULT_TTL, Clock.systemUTC(), true);
    }

    BoundedForwardDnsLookup(
            ForwardDnsLookup delegate,
            ExecutorService executor,
            Duration timeout,
            Duration ttl,
            Clock clock,
            boolean ownsExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeout = requirePositive(timeout, "timeout");
        this.ttl = requirePositive(ttl, "ttl");
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.ownsExecutor = ownsExecutor;
    }

    /** Test helper with injectable delegate / timing. */
    public static BoundedForwardDnsLookup forTests(
            ForwardDnsLookup delegate, Duration timeout, Duration ttl, Clock clock) {
        return new BoundedForwardDnsLookup(
                delegate, newBoundedPool(2), timeout, ttl, clock != null ? clock : Clock.systemUTC(), true);
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
        Future<InetAddress[]> future = executor.submit(() -> delegate.resolve(hostname));
        try {
            InetAddress[] addresses = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            InetAddress[] safe = addresses == null ? new InetAddress[0] : addresses;
            cache.put(key, CacheEntry.ok(safe, now.plus(ttl)));
            return safe;
        } catch (TimeoutException ex) {
            future.cancel(true);
            SocketTimeoutException timeoutEx = new SocketTimeoutException("DNS lookup timed out for " + hostname);
            cache.put(key, CacheEntry.fail(timeoutEx, now.plus(ttl)));
            throw timeoutEx;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            Exception failure = cause instanceof Exception checked ? checked : new Exception(cause);
            cache.put(key, CacheEntry.fail(failure, now.plus(ttl)));
            throw failure;
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    int cacheSizeForTests() {
        return cache.size();
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

    private static ExecutorService newBoundedPool(int size) {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pingui-fwd-dns-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(Math.max(1, size), factory);
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
