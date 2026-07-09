package io.pingui.persistence;

import java.time.Instant;
import java.util.Objects;

/** One row from {@code persistence_event} (P11-020 query). */
public record PersistenceEventRecord(
        long id, PersistenceEventType eventType, String host, String profile, String payloadJson, Instant observedAt) {

    public PersistenceEventRecord {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
