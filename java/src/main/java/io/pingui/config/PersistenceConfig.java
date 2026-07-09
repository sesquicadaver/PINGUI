package io.pingui.config;

import io.pingui.persistence.PersistencePolicy;
import java.nio.file.Path;
import java.util.Optional;

/** Per-profile persistence: optional SQLite path and event toggles (P11-016). */
public record PersistenceConfig(Optional<Path> sessionDb, PersistenceEventsConfig events) {

    public PersistenceConfig {
        events = events != null ? events : PersistenceEventsConfig.defaults();
        sessionDb = sessionDb != null ? sessionDb : Optional.empty();
    }

    public static PersistenceConfig defaults() {
        return new PersistenceConfig(Optional.empty(), PersistenceEventsConfig.defaults());
    }

    public static PersistenceConfig eventsOnly(PersistenceEventsConfig events) {
        return new PersistenceConfig(Optional.empty(), events);
    }

    public PersistenceConfig withSessionDb(Path path) {
        return new PersistenceConfig(Optional.of(path), events);
    }

    public PersistenceConfig withSessionDb(Optional<Path> path) {
        return new PersistenceConfig(path, events);
    }

    public PersistenceConfig withEvents(PersistenceEventsConfig newEvents) {
        return new PersistenceConfig(sessionDb, newEvents);
    }

    public boolean routeChange() {
        return events.routeChange();
    }

    public boolean probeError() {
        return events.probeError();
    }

    public PersistencePolicy toPolicy() {
        return events.toPolicy();
    }

    public boolean isDefault() {
        return sessionDb.isEmpty() && events.isDefault();
    }
}
