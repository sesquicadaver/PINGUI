package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.config.HostEntry;
import io.pingui.config.TracingProfile;
import io.pingui.i18n.UiI18n;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistencePolicy;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.stage.Window;

/** Constructs {@link MonitorService} with JavaFX listener wiring for the main window. */
final class MonitorServiceFactory {
    private final AppOptions options;
    private final Supplier<SessionStore> store;
    private final Supplier<String> activeProfileName;
    private final MonitorUiHandler monitorUi;
    private final HostListPresenter hostListPresenter;
    private final UserFeedback userFeedback;
    private final Supplier<Window> dialogOwner;
    private final Supplier<Optional<PersistencePolicy>> sessionPersistenceOverride;
    private final Consumer<MonitorService> attachTelemetry;

    MonitorServiceFactory(
            AppOptions options,
            Supplier<SessionStore> store,
            Supplier<String> activeProfileName,
            MonitorUiHandler monitorUi,
            HostListPresenter hostListPresenter,
            UserFeedback userFeedback,
            Supplier<Window> dialogOwner,
            Supplier<Optional<PersistencePolicy>> sessionPersistenceOverride,
            Consumer<MonitorService> attachTelemetry) {
        this.options = options;
        this.store = store;
        this.activeProfileName = activeProfileName;
        this.monitorUi = monitorUi;
        this.hostListPresenter = hostListPresenter;
        this.userFeedback = userFeedback;
        this.dialogOwner = dialogOwner;
        this.sessionPersistenceOverride = sessionPersistenceOverride;
        this.attachTelemetry = attachTelemetry;
    }

    MonitorService create(TracingProfile profile, List<HostEntry> sessionHosts) {
        SessionStore sessionStore = store.get();
        MonitorService service = MonitorLifecycle.create(
                profile,
                activeProfileName.get(),
                sessionStore,
                new MonitorService.Listener() {
                    @Override
                    public void onDataReceived(String host, RouteSnapshot snapshot) {
                        Platform.runLater(() -> monitorUi.handleData(host, snapshot));
                    }

                    @Override
                    public void onDataReceived(
                            String host, RouteSnapshot snapshot, io.pingui.monitor.PollSampleScope sampleScope) {
                        Platform.runLater(() -> monitorUi.handleData(host, snapshot, sampleScope));
                    }

                    @Override
                    public void onDataReceived(
                            String host,
                            RouteSnapshot snapshot,
                            io.pingui.monitor.PollSampleScope sampleScope,
                            boolean routeChanged) {
                        Platform.runLater(() -> monitorUi.handleData(host, snapshot, sampleScope, routeChanged));
                    }

                    @Override
                    public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {
                        Platform.runLater(() -> monitorUi.handleRouteChanged(host, oldIps, newIps));
                    }

                    @Override
                    public void onProbeError(String host, String message) {
                        Platform.runLater(() -> {
                            userFeedback.info(UiI18n.get("status.probe", host, message));
                            HostItem item = hostListPresenter.findItem(host);
                            if (item != null) {
                                hostListPresenter.syncMetrics(item);
                            }
                        });
                    }

                    @Override
                    public void onPollFinished(String host) {
                        Platform.runLater(() -> {
                            HostItem item = hostListPresenter.findItem(host);
                            if (item != null) {
                                hostListPresenter.syncMetrics(item);
                            }
                        });
                    }
                },
                options.alertOverrides().applyTo(profile.alerts()),
                sessionStore.database(),
                sessionHosts,
                MonitorLifecycle.javaFxDesktopSink(dialogOwner));
        PersistencePolicy baseline =
                options.persistenceOverrides().applyTo(profile.persistence()).toPolicy();
        PersistencePolicy effective = sessionPersistenceOverride.get().orElse(baseline);
        service.setPendingPersistencePolicy(effective);
        service.persistencePolicy().applyPendingAfterCycle();
        attachTelemetry.accept(service);
        return service;
    }
}
