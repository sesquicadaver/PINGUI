package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.TelemetryAttachment;
import io.pingui.config.AlertConfig;
import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.TracingProfile;
import io.pingui.i18n.UiI18n;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistencePolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.stage.Window;

/** Settings-menu dialogs: export, profile params, persistence, alerts, telemetry. */
final class SettingsDialogsCoordinator {
    private final Supplier<SessionStore> store;
    private final Supplier<MonitorService> monitor;
    private final Supplier<ProfileDocument> profileDocument;
    private final AppOptions options;
    private final Supplier<Window> dialogOwner;
    private final UserFeedback userFeedback;
    private final AppStatusPresenter appStatus;
    private final Runnable markDirty;
    private final Consumer<List<HostEntry>> recreateMonitor;
    private final Runnable attachTelemetry;
    private final Supplier<TelemetryAttachment> telemetry;
    private final Consumer<Optional<PersistencePolicy>> reconnectPersistence;
    private final Supplier<Optional<Path>> resolveSessionDbPath;
    private final Supplier<Optional<PersistencePolicy>> sessionPersistenceOverride;
    private final Consumer<Optional<Path>> setSessionGuiDbOverride;
    private final Consumer<Optional<PersistencePolicy>> setSessionPersistenceOverride;

    SettingsDialogsCoordinator(
            Supplier<SessionStore> store,
            Supplier<MonitorService> monitor,
            Supplier<ProfileDocument> profileDocument,
            AppOptions options,
            Supplier<Window> dialogOwner,
            UserFeedback userFeedback,
            AppStatusPresenter appStatus,
            Runnable markDirty,
            Consumer<List<HostEntry>> recreateMonitor,
            Runnable attachTelemetry,
            Supplier<TelemetryAttachment> telemetry,
            Consumer<Optional<PersistencePolicy>> reconnectPersistence,
            Supplier<Optional<Path>> resolveSessionDbPath,
            Supplier<Optional<PersistencePolicy>> sessionPersistenceOverride,
            Consumer<Optional<Path>> setSessionGuiDbOverride,
            Consumer<Optional<PersistencePolicy>> setSessionPersistenceOverride) {
        this.store = store;
        this.monitor = monitor;
        this.profileDocument = profileDocument;
        this.options = options;
        this.dialogOwner = dialogOwner;
        this.userFeedback = userFeedback;
        this.appStatus = appStatus;
        this.markDirty = markDirty;
        this.recreateMonitor = recreateMonitor;
        this.attachTelemetry = attachTelemetry;
        this.telemetry = telemetry;
        this.reconnectPersistence = reconnectPersistence;
        this.resolveSessionDbPath = resolveSessionDbPath;
        this.sessionPersistenceOverride = sessionPersistenceOverride;
        this.setSessionGuiDbOverride = setSessionGuiDbOverride;
        this.setSessionPersistenceOverride = setSessionPersistenceOverride;
    }

    void onExportNow() {
        SessionStore sessionStore = store.get();
        if (!sessionStore.hasPersistence() || sessionStore.database() == null) {
            userFeedback.error(SessionExportUi.noSqliteMessage());
            return;
        }
        appStatus.beginProgress(UiI18n.get("status.exporting"), null);
        try {
            Optional<Path> written = SessionExportUi.chooseAndExport(dialogOwner.get(), sessionStore.database());
            written.ifPresent(path -> userFeedback.info(SessionExportUi.successMessage(path)));
        } catch (IOException | RuntimeException ex) {
            userFeedback.error(SessionExportUi.failureMessage(ex));
        } finally {
            appStatus.endProgress();
        }
    }

    void onProfileParamsSettings() {
        ProfileParamsSettingsDialog.show(
                dialogOwner.get(),
                profileDocument.get().active(),
                options.profileOverrides(),
                this::handleProfileParamsSettings);
    }

    private void handleProfileParamsSettings(ProfileParamsSettingsDialog.Result result) {
        List<HostEntry> liveEntries = HostViewRules.entriesForConfig(store.get().toHostEntries());
        TracingProfile next = profileDocument
                .get()
                .active()
                .withPollSettings(
                        result.intervalSeconds(), result.maxHops(), result.timeoutSeconds(), result.probeMode())
                .withHosts(liveEntries);
        profileDocument.get().putProfile(profileDocument.get().activeProfile(), next);
        recreateMonitor.accept(liveEntries);
        markDirty.run();
        userFeedback.info(UiI18n.get(
                "status.profile_params_applied",
                next.intervalSeconds(),
                next.maxHops(),
                next.timeoutSeconds(),
                next.probeMode().cliValue()));
    }

    void onPersistenceSettings() {
        SessionStore sessionStore = store.get();
        MonitorService monitorService = monitor.get();
        PersistencePolicy active = sessionStore.hasPersistence()
                ? monitorService.persistencePolicy().active()
                : PersistencePolicy.defaults();
        PersistencePolicy pending = sessionStore.hasPersistence()
                ? monitorService.persistencePolicy().pending()
                : sessionPersistenceOverride.get().orElseGet(() -> options.persistenceOverrides()
                        .applyTo(profileDocument.get().active().persistence())
                        .toPolicy());
        PersistenceSettingsDialog.show(
                dialogOwner.get(),
                resolveSessionDbPath.get(),
                options.sessionDbPath(),
                profileDocument.get().active().persistence().sessionDb(),
                options.persistenceOverrides(),
                active,
                pending,
                sessionStore.database(),
                this::handlePersistenceSettings);
    }

    private void handlePersistenceSettings(PersistenceSettingsDialog.Result result) {
        if (result.sessionDbPath().isPresent()) {
            setSessionGuiDbOverride.accept(result.sessionDbPath());
            reconnectPersistence.accept(Optional.of(result.policy()));
            notifyPersistenceConnected(result.sessionDbPath().get());
        } else {
            setSessionPersistenceOverride.accept(Optional.of(result.policy()));
            monitor.get().setPendingPersistencePolicy(result.policy());
        }
        userFeedback.info(UiI18n.get("status.persistence_updated"));
        if (result.sessionDbPath().isPresent()) {
            markDirty.run();
        }
    }

    void onAlertsSettings() {
        AlertsSettingsDialog.show(
                dialogOwner.get(),
                options.alertOverrides().applyTo(profileDocument.get().active().alerts()),
                options.alertOverrides(),
                this::handleAlertsSettings);
    }

    private void handleAlertsSettings(AlertsSettingsDialog.Result result) {
        TracingProfile active = profileDocument.get().active();
        profileDocument.get().putProfile(profileDocument.get().activeProfile(), active.withAlerts(result.alerts()));
        AlertConfig effective = options.alertOverrides().applyTo(result.alerts());
        MonitorLifecycle.applyAlertDispatcher(
                monitor.get(), effective, MonitorLifecycle.javaFxDesktopSink(dialogOwner));
        MonitorLifecycle.applyAlertRules(monitor.get(), effective);
        markDirty.run();
        userFeedback.info(UiI18n.get("status.alerts_updated", result.alerts().toRedactedString()));
    }

    void onTelemetrySettings() {
        TelemetrySettingsDialog.show(
                dialogOwner.get(),
                profileDocument.get().active().telemetry(),
                options.telemetryOverrides(),
                this::handleTelemetrySettings);
    }

    private void handleTelemetrySettings(TelemetrySettingsDialog.Result result) {
        TracingProfile active = profileDocument.get().active();
        profileDocument
                .get()
                .putProfile(profileDocument.get().activeProfile(), active.withTelemetry(result.telemetry()));
        attachTelemetry.run();
        TelemetryAttachment attachment = telemetry.get();
        String sinks = attachment != null && !attachment.registeredIds().isEmpty()
                ? String.join(", ", attachment.registeredIds())
                : UiI18n.get("status.no_sinks");
        userFeedback.info(
                UiI18n.get("status.telemetry_updated", sinks, result.telemetry().toRedactedString()));
        markDirty.run();
    }

    private void notifyPersistenceConnected(Path dbPath) {
        userFeedback.info(UiI18n.get("status.sqlite_connected", dbPath.toAbsolutePath()));
    }
}
