package io.pingui.dns;

import io.pingui.geoip.IpLiterals;
import io.pingui.model.Models;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Async reverse-DNS (PTR) for hop labels with a positive/negative cache.
 *
 * <p>Lookups never run on the caller thread: {@link #labelLine(String)} / {@link #cachedHostname(String)}
 * return only cached values and schedule background resolution on miss. Cache TTL defaults to 5
 * minutes (ROADMAP P14-031).
 */
public final class DnsResolver {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new DaemonFactory("pingui-rdns"));

    private static volatile boolean enabled = true;
    private static volatile Duration ttl = DEFAULT_TTL;
    private static volatile Clock clock = Clock.systemUTC();
    private static volatile Function<InetAddress, String> reverseLookup = DnsResolver::defaultReverse;

    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private DnsResolver() {}

    /** Enables or disables reverse DNS (cache retained). */
    public static void configure(boolean rdnsEnabled) {
        enabled = rdnsEnabled;
    }

    /**
     * Test hook: replace clock, TTL, and reverse implementation; clears cache and in-flight set.
     */
    public static void configureForTests(
            boolean rdnsEnabled, Duration cacheTtl, Clock testClock, Function<InetAddress, String> lookup) {
        enabled = rdnsEnabled;
        ttl = cacheTtl != null && !cacheTtl.isNegative() && !cacheTtl.isZero() ? cacheTtl : DEFAULT_TTL;
        clock = testClock != null ? testClock : Clock.systemUTC();
        reverseLookup = lookup != null ? lookup : DnsResolver::defaultReverse;
        CACHE.clear();
        IN_FLIGHT.clear();
    }

    /** Clears cache and restores production defaults (keeps listeners). */
    public static void resetForTests() {
        configureForTests(true, DEFAULT_TTL, Clock.systemUTC(), DnsResolver::defaultReverse);
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.addIfAbsent(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    /**
     * Returns a hop-label line {@code "\nhostname"} when a non-expired positive cache entry exists;
     * otherwise {@code ""} and schedules an async lookup for IP literals.
     */
    public static String labelLine(String ip) {
        String hostname = cachedHostname(ip);
        return hostname != null ? "\n" + hostname : "";
    }

    /**
     * @return cached PTR hostname, or {@code null} when unknown / disabled / not a literal / negative
     *     cache hit
     */
    public static String cachedHostname(String ip) {
        if (!enabled || ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
            return null;
        }
        String key = cacheKey(ip);
        if (key == null) {
            return null;
        }
        CacheEntry entry = CACHE.get(key);
        Instant now = clock.instant();
        if (entry != null && entry.expiresAt.isAfter(now)) {
            return entry.hostname;
        }
        if (entry != null) {
            CACHE.remove(key, entry);
        }
        scheduleLookup(key);
        return null;
    }

    /** Seeds the cache (tests / warm start). {@code hostname == null} stores a negative entry. */
    public static void putCache(String ip, String hostname) {
        String key = cacheKey(ip);
        if (key == null) {
            return;
        }
        Instant expires = clock.instant().plus(ttl);
        CACHE.put(key, new CacheEntry(hostname, expires));
    }

    static int cacheSizeForTests() {
        return CACHE.size();
    }

    private static void scheduleLookup(String key) {
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                resolveAndStore(key);
            } finally {
                IN_FLIGHT.remove(key);
            }
        });
    }

    private static void resolveAndStore(String key) {
        InetAddress literal = IpLiterals.parseLiteralOrNull(key);
        if (literal == null) {
            return;
        }
        String hostname = null;
        try {
            hostname = reverseLookup.apply(literal);
        } catch (RuntimeException ignored) {
            hostname = null;
        }
        if (hostname != null) {
            hostname = hostname.strip();
            if (hostname.isEmpty() || isSameAsAddress(hostname, literal)) {
                hostname = null;
            }
        }
        Instant expires = clock.instant().plus(ttl);
        CacheEntry previous = CACHE.put(key, new CacheEntry(hostname, expires));
        boolean changed = previous == null
                || !Objects.equals(previous.hostname, hostname)
                || previous.expiresAt.isBefore(clock.instant());
        if (hostname != null && changed) {
            notifyListeners();
        }
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // Listener failures must not break the resolver thread.
            }
        }
    }

    private static String cacheKey(String raw) {
        InetAddress literal = IpLiterals.parseLiteralOrNull(raw);
        if (literal == null) {
            return null;
        }
        return literal.getHostAddress();
    }

    private static String defaultReverse(InetAddress literal) {
        try {
            InetAddress query = InetAddress.getByAddress(literal.getAddress());
            return query.getCanonicalHostName();
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean isSameAsAddress(String hostname, InetAddress literal) {
        String address = literal.getHostAddress();
        if (hostname.equalsIgnoreCase(address)) {
            return true;
        }
        // Strip zone / brackets noise if present.
        String stripped = hostname;
        if (stripped.startsWith("[") && stripped.endsWith("]") && stripped.length() >= 2) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return stripped.equalsIgnoreCase(address);
    }

    private record CacheEntry(String hostname, Instant expiresAt) {}

    private static final class DaemonFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger();

        DaemonFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
