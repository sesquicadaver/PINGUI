package io.pingui.geoip;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded offline IP enrichment service (P36-006).
 *
 * <p>Precedence: YAML override → MMDB → {@link IpMetadataSource#NONE}. Lookups never perform DNS or
 * HTTP. Probe/monitor paths must use {@link #cached(String)} / {@link #offer(String)} only —
 * {@link #resolve(String)} may block on local MMDB I/O and is for bootstrap/tests.
 *
 * <p>Resources are bounded: LRU (+ negative) cache, AbortPolicy queue, at most one in-flight lookup
 * per canonical IP. Atomic reload keeps the previous provider when the new file fails to open.
 */
public final class IpMetadataService implements AutoCloseable {
    public static final int DEFAULT_CACHE_CAPACITY = 4096;
    public static final int DEFAULT_QUEUE_CAPACITY = 64;
    public static final int DEFAULT_POOL_SIZE = 1;

    private final int cacheCapacity;
    private final int queueCapacity;
    private final ThreadPoolExecutor executor;
    private final Object cacheLock = new Object();
    private final LinkedHashMap<String, IpMetadata> cache;
    private final ConcurrentHashMap<String, CompletableFuture<IpMetadata>> inFlight = new ConcurrentHashMap<>();
    private final AtomicReference<Providers> providers;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong unknowns = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong reloadFailures = new AtomicLong();
    private final AtomicReference<Instant> lastSuccessfulReload = new AtomicReference<>();

    /**
     * @param overrides optional YAML overrides ({@code null} = none)
     * @param mmdb optional MMDB provider ({@code null} = none); ownership transfers to this service
     */
    public IpMetadataService(IpMetadataProvider overrides, MmdbIpMetadataProvider mmdb) {
        this(overrides, mmdb, DEFAULT_CACHE_CAPACITY, DEFAULT_QUEUE_CAPACITY, DEFAULT_POOL_SIZE);
    }

    public IpMetadataService(
            IpMetadataProvider overrides,
            MmdbIpMetadataProvider mmdb,
            int cacheCapacity,
            int queueCapacity,
            int poolSize) {
        if (cacheCapacity < 1) {
            throw new IllegalArgumentException("cacheCapacity must be >= 1");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be >= 1");
        }
        this.cacheCapacity = cacheCapacity;
        this.queueCapacity = queueCapacity;
        this.providers = new AtomicReference<>(new Providers(overrides, mmdb));
        this.cache = new LinkedHashMap<>(Math.min(16, cacheCapacity), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, IpMetadata> eldest) {
                return size() > IpMetadataService.this.cacheCapacity;
            }
        };
        this.executor = newBoundedPool(poolSize, queueCapacity);
        this.lastSuccessfulReload.set(Instant.now());
    }

    /** Overrides-only / disabled MMDB service with default bounds. */
    public static IpMetadataService overridesOnly(IpMetadataProvider overrides) {
        return new IpMetadataService(overrides, null);
    }

    /** Empty enrichment (always {@link IpMetadataSource#NONE} for literals). */
    public static IpMetadataService disabled() {
        return new IpMetadataService(null, null);
    }

    /**
     * Cache-only read for GUI / probe-adjacent paths. Does not trigger lookup.
     *
     * @return cached snapshot, or {@code null} on miss / non-literal
     */
    public IpMetadata cached(String ip) {
        String key = IpLiterals.canonicalLiteralOrNull(ip);
        if (key == null) {
            return null;
        }
        synchronized (cacheLock) {
            IpMetadata hit = cache.get(key);
            if (hit != null) {
                hits.incrementAndGet();
            }
            return hit;
        }
    }

    /**
     * Synchronous resolve with LRU/negative cache. Local I/O only — do not call from probe critical
     * path.
     *
     * @return enrichment, or {@code null} when {@code ip} is not an address literal
     */
    public IpMetadata resolve(String ip) {
        String key = IpLiterals.canonicalLiteralOrNull(ip);
        if (key == null) {
            return null;
        }
        IpMetadata cachedHit = cachedWithoutHitCounter(key);
        if (cachedHit != null) {
            hits.incrementAndGet();
            return cachedHit;
        }

        CompletableFuture<IpMetadata> created = new CompletableFuture<>();
        CompletableFuture<IpMetadata> shared = inFlight.putIfAbsent(key, created);
        if (shared != null) {
            coalesced.incrementAndGet();
            try {
                return shared.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                errors.incrementAndGet();
                return null;
            } catch (ExecutionException ex) {
                errors.incrementAndGet();
                return null;
            }
        }
        try {
            // Another resolve may have finished between the first cache miss and inFlight win.
            IpMetadata raced = cachedWithoutHitCounter(key);
            if (raced != null) {
                hits.incrementAndGet();
                created.complete(raced);
                return raced;
            }
            misses.incrementAndGet();
            Providers snapshot = providers.get();
            IpMetadata resolved = lookupUncached(snapshot, key);
            if (providers.get() == snapshot) {
                putCache(key, resolved);
            }
            created.complete(resolved);
            return resolved;
        } catch (RuntimeException ex) {
            errors.incrementAndGet();
            created.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(key, created);
        }
    }

    /**
     * Enqueue background enrichment for a literal. Coalesces duplicate IPs; AbortPolicy overflow
     * increments {@link GeoIpOpsStats#rejected()} and returns {@code false} without blocking the
     * caller.
     *
     * @return {@code true} when accepted, coalesced, or already cached; {@code false} when rejected
     */
    public boolean offer(String ip) {
        String key = IpLiterals.canonicalLiteralOrNull(ip);
        if (key == null) {
            return false;
        }
        if (cachedWithoutHitCounter(key) != null) {
            return true;
        }
        CompletableFuture<IpMetadata> created = new CompletableFuture<>();
        CompletableFuture<IpMetadata> shared = inFlight.putIfAbsent(key, created);
        if (shared != null) {
            coalesced.incrementAndGet();
            return true;
        }
        try {
            executor.execute(() -> {
                try {
                    IpMetadata raced = cachedWithoutHitCounter(key);
                    if (raced != null) {
                        created.complete(raced);
                        return;
                    }
                    misses.incrementAndGet();
                    Providers snapshot = providers.get();
                    IpMetadata resolved = lookupUncached(snapshot, key);
                    if (providers.get() == snapshot) {
                        putCache(key, resolved);
                    }
                    created.complete(resolved);
                } catch (RuntimeException ex) {
                    errors.incrementAndGet();
                    created.completeExceptionally(ex);
                } finally {
                    inFlight.remove(key, created);
                }
            });
            return true;
        } catch (RejectedExecutionException ex) {
            inFlight.remove(key, created);
            rejected.incrementAndGet();
            return false;
        }
    }

    /**
     * Atomically replace MMDB readers. On open failure the previous provider stays active and
     * {@link GeoIpOpsStats#reloadFailures()} increments.
     *
     * @param geoDb City/Country MMDB path; {@code null} clears MMDB
     * @param asnDb optional ASN MMDB; ignored when {@code geoDb} is {@code null}
     * @return {@code true} when the swap succeeded
     */
    public boolean reloadMmdb(Path geoDb, Path asnDb) {
        MmdbIpMetadataProvider next;
        try {
            next = geoDb == null ? null : MmdbIpMetadataProvider.open(geoDb, asnDb);
        } catch (RuntimeException ex) {
            reloadFailures.incrementAndGet();
            return false;
        }
        Providers previous = providers.getAndUpdate(cur -> new Providers(cur.overrides(), next));
        clearCache();
        lastSuccessfulReload.set(Instant.now());
        closeQuietly(previous.mmdb());
        return true;
    }

    /**
     * Atomically replace YAML overrides. {@code null} clears overrides. Always succeeds (YAML parse
     * errors must be handled by the caller before invoking this).
     */
    public void reloadOverrides(IpMetadataProvider overrides) {
        Providers previous = providers.getAndUpdate(cur -> new Providers(overrides, cur.mmdb()));
        clearCache();
        lastSuccessfulReload.set(Instant.now());
        // overrides are not Closeable today; keep previous reference drop for GC
        Objects.requireNonNull(previous);
    }

    /** Current provider bundle (tests / ops). */
    public IpMetadataProvider overrides() {
        return providers.get().overrides();
    }

    public MmdbIpMetadataProvider mmdb() {
        return providers.get().mmdb();
    }

    public GeoIpOpsStats opsStats() {
        int cacheSize;
        synchronized (cacheLock) {
            cacheSize = cache.size();
        }
        return new GeoIpOpsStats(
                cacheSize,
                cacheCapacity,
                executor.getQueue().size(),
                queueCapacity,
                inFlight.size(),
                hits.get(),
                misses.get(),
                unknowns.get(),
                errors.get(),
                rejected.get(),
                coalesced.get(),
                reloadFailures.get(),
                lastSuccessfulReload.get());
    }

    int cacheSizeForTests() {
        synchronized (cacheLock) {
            return cache.size();
        }
    }

    int inFlightSizeForTests() {
        return inFlight.size();
    }

    private IpMetadata cachedWithoutHitCounter(String key) {
        synchronized (cacheLock) {
            return cache.get(key);
        }
    }

    private void putCache(String key, IpMetadata value) {
        if (value == null) {
            return;
        }
        synchronized (cacheLock) {
            cache.put(key, value);
        }
        if (value.source() == IpMetadataSource.NONE) {
            unknowns.incrementAndGet();
        }
    }

    private void clearCache() {
        synchronized (cacheLock) {
            cache.clear();
        }
    }

    private IpMetadata lookupUncached(Providers current, String canonicalIp) {
        InetAddress address = IpLiterals.parseLiteralOrNull(canonicalIp);
        if (address == null) {
            return null;
        }
        IpAddressScope scope = IpAddressClassifier.scopeOf(address);
        if (current.overrides() != null) {
            IpMetadata override = current.overrides().lookup(canonicalIp);
            if (override != null) {
                return override;
            }
        }
        if (current.mmdb() != null) {
            IpMetadata mmdbHit = current.mmdb().lookup(canonicalIp);
            if (mmdbHit != null) {
                return mmdbHit;
            }
        }
        return IpMetadata.unknown(canonicalIp, scope);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        Providers current = providers.getAndSet(new Providers(null, null));
        closeQuietly(current.mmdb());
        clearCache();
        inFlight.clear();
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // best-effort during reload/shutdown
        }
    }

    private static ThreadPoolExecutor newBoundedPool(int poolSize, int queueCapacity) {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pingui-geoip-" + seq.incrementAndGet());
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

    private record Providers(IpMetadataProvider overrides, MmdbIpMetadataProvider mmdb) {}
}
