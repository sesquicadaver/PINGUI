package io.pingui.config;

import java.time.Instant;
import java.util.Objects;

/**
 * One maintenance / alert-silence rule (P29-003). Monitoring continues; only alert dispatch is
 * suppressed while {@code until} is in the future.
 */
public record AlertSilenceEntry(AlertSilenceScope scope, String match, Instant until, String reason) {
    public AlertSilenceEntry {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(until, "until");
        match = match == null ? "" : match.strip();
        if (scope != AlertSilenceScope.PROFILE && match.isBlank()) {
            throw new IllegalArgumentException("silence match required for host/tag scope");
        }
        if (scope == AlertSilenceScope.PROFILE) {
            match = match.isBlank() ? "*" : match;
        }
        reason = reason == null ? "" : reason.strip();
    }

    /** True when this rule still applies at {@code now}. */
    public boolean isActive(Instant now) {
        Instant at = now != null ? now : Instant.now();
        return until.isAfter(at);
    }
}
