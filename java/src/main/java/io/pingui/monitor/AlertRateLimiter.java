package io.pingui.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-host rolling one-hour alert rate limit (P10-040 / ADR_ALERTS). */
public final class AlertRateLimiter {
    private static final double WINDOW_SECONDS = 3600.0;

    private final int maxPerHour;
    private final Map<String, List<Double>> history = new HashMap<>();

    public AlertRateLimiter(int maxPerHour) {
        if (maxPerHour < 1) {
            throw new IllegalArgumentException("maxPerHour must be >= 1");
        }
        this.maxPerHour = maxPerHour;
    }

    public boolean allow(String host) {
        return allow(host, System.currentTimeMillis() / 1000.0);
    }

    boolean allow(String host, double nowSeconds) {
        double windowStart = nowSeconds - WINDOW_SECONDS;
        List<Double> entries = new ArrayList<>();
        for (double timestamp : history.getOrDefault(host, List.of())) {
            if (timestamp > windowStart) {
                entries.add(timestamp);
            }
        }
        if (entries.size() >= maxPerHour) {
            history.put(host, entries);
            return false;
        }
        entries.add(nowSeconds);
        history.put(host, entries);
        return true;
    }
}
