package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.SessionDbResolver;
import io.pingui.config.TracingProfile;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.timeseries.TimeSeriesBackends;
import io.pingui.persistence.timeseries.TimeSeriesConfigException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Session store lifecycle: SQLite open, time-series attach, profile/persistence reload. */
final class PersistenceSessionCoordinator {
    private Optional<PersistencePolicy> sessionPersistenceOverride = Optional.empty();
    private Optional<Path> sessionGuiDbOverride = Optional.empty();

    private final AppOptions options;
    private final Supplier<ProfileDocument> profileDocument;
    private final Consumer<SessionStore> setStore;
    private final Supplier<SessionStore> store;
    private final Consumer<List<HostEntry>> createMonitor;
    private final Runnable closeMonitorAndTelemetry;
    private final Runnable updateHistoryPanelVisibility;
    private final Consumer<List<HostEntry>> rebuildHostList;
    private final Runnable syncHistoryHostFilter;
    private final Runnable resetReplayState;
    private final Runnable redrawRouteGraph;
    private final Runnable applyViewMode;
    private final Runnable clearHostSelection;
    private final Runnable syncInputLimits;

    PersistenceSessionCoordinator(
            AppOptions options,
            Supplier<ProfileDocument> profileDocument,
            Consumer<SessionStore> setStore,
            Supplier<SessionStore> store,
            Consumer<List<HostEntry>> createMonitor,
            Runnable closeMonitorAndTelemetry,
            Runnable updateHistoryPanelVisibility,
            Consumer<List<HostEntry>> rebuildHostList,
            Runnable syncHistoryHostFilter,
            Runnable resetReplayState,
            Runnable redrawRouteGraph,
            Runnable applyViewMode,
            Runnable clearHostSelection,
            Runnable syncInputLimits) {
        this.options = options;
        this.profileDocument = profileDocument;
        this.setStore = setStore;
        this.store = store;
        this.createMonitor = createMonitor;
        this.closeMonitorAndTelemetry = closeMonitorAndTelemetry;
        this.updateHistoryPanelVisibility = updateHistoryPanelVisibility;
        this.rebuildHostList = rebuildHostList;
        this.syncHistoryHostFilter = syncHistoryHostFilter;
        this.resetReplayState = resetReplayState;
        this.redrawRouteGraph = redrawRouteGraph;
        this.applyViewMode = applyViewMode;
        this.clearHostSelection = clearHostSelection;
        this.syncInputLimits = syncInputLimits;
    }

    Optional<PersistencePolicy> sessionPersistenceOverride() {
        return sessionPersistenceOverride;
    }

    Optional<Path> sessionGuiDbOverride() {
        return sessionGuiDbOverride;
    }

    void clearSessionOverrides() {
        sessionPersistenceOverride = Optional.empty();
        sessionGuiDbOverride = Optional.empty();
    }

    void setSessionGuiDbOverride(Optional<Path> path) {
        sessionGuiDbOverride = path != null ? path : Optional.empty();
    }

    void setSessionPersistenceOverride(Optional<PersistencePolicy> policy) {
        sessionPersistenceOverride = policy != null ? policy : Optional.empty();
    }

    Optional<Path> resolveSessionDbPath() {
        return SessionDbResolver.resolve(
                options.sessionDbPath(),
                profileDocument.get().active().persistence().sessionDb(),
                sessionGuiDbOverride);
    }

    io.pingui.persistence.SessionDatabase openSessionDatabase() {
        return resolveSessionDbPath()
                .map(io.pingui.persistence.SessionDatabase::new)
                .orElse(null);
    }

    void attachTimeSeries(SessionStore sessionStore) {
        try {
            var backend = TimeSeriesBackends.create(options.timeSeriesOverrides());
            if (backend != null) {
                sessionStore.setTimeSeriesBackend(backend);
            }
        } catch (TimeSeriesConfigException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    void reconnectPersistence(Optional<PersistencePolicy> policyOverride) {
        List<HostEntry> liveEntries = HostViewRules.entriesForConfig(store.get().toHostEntries());
        TracingProfile profile = profileDocument.get().active();
        closeMonitorAndTelemetry.run();
        store.get().close();
        SessionStore nextStore = SessionStore.fromEntries(liveEntries, openSessionDatabase(), profile.hostProbeMode());
        attachTimeSeries(nextStore);
        setStore.accept(nextStore);
        sessionPersistenceOverride = policyOverride != null ? policyOverride : Optional.empty();
        createMonitor.accept(liveEntries);
        finishSessionSwap(liveEntries);
    }

    void reloadActiveProfile() {
        clearSessionOverrides();
        TracingProfile profile = profileDocument.get().active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(profile.hosts());
        closeMonitorAndTelemetry.run();
        store.get().close();
        SessionStore nextStore = SessionStore.fromEntries(sessionHosts, openSessionDatabase(), profile.hostProbeMode());
        attachTimeSeries(nextStore);
        setStore.accept(nextStore);
        createMonitor.accept(sessionHosts);
        finishSessionSwap(sessionHosts);
    }

    private void finishSessionSwap(List<HostEntry> entries) {
        updateHistoryPanelVisibility.run();
        rebuildHostList.accept(entries);
        syncHistoryHostFilter.run();
        clearHostSelection.run();
        syncInputLimits.run();
        applyViewMode.run();
        resetReplayState.run();
        redrawRouteGraph.run();
    }
}
