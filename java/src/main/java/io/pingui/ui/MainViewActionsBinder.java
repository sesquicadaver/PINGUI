package io.pingui.ui;

import io.pingui.i18n.UiLocale;
import io.pingui.ui.view.MainViewActions;
import java.util.function.Supplier;
import javafx.stage.Window;

/** Wires {@link MainViewActions} menu callbacks to MainController coordinators. */
final class MainViewActionsBinder {
    private final HostListPresenter hostListPresenter;
    private final ProfileUiCoordinator profileUi;
    private final Runnable refreshRouteHistory;
    private final Supplier<Window> dialogOwner;
    private final SettingsDialogsCoordinator settingsDialogs;
    private final Runnable onSaveConfig;
    private final java.util.function.Consumer<UiLocale> applyUiLocale;

    MainViewActionsBinder(
            HostListPresenter hostListPresenter,
            ProfileUiCoordinator profileUi,
            Runnable refreshRouteHistory,
            Supplier<Window> dialogOwner,
            SettingsDialogsCoordinator settingsDialogs,
            Runnable onSaveConfig,
            java.util.function.Consumer<UiLocale> applyUiLocale) {
        this.hostListPresenter = hostListPresenter;
        this.profileUi = profileUi;
        this.refreshRouteHistory = refreshRouteHistory;
        this.dialogOwner = dialogOwner;
        this.settingsDialogs = settingsDialogs;
        this.onSaveConfig = onSaveConfig;
        this.applyUiLocale = applyUiLocale;
    }

    MainViewActions bind() {
        return new MainViewActions() {
            @Override
            public void onSaveConfig() {
                onSaveConfig.run();
            }

            @Override
            public void onAddHost() {
                hostListPresenter.addHost();
            }

            @Override
            public void onEditHost() {
                hostListPresenter.editHost();
            }

            @Override
            public void onEditTags() {
                hostListPresenter.editSelectedHostTags();
            }

            @Override
            public void onRemoveHost() {
                hostListPresenter.removeHost();
            }

            @Override
            public void onNewProfile() {
                profileUi.onNewProfile();
            }

            @Override
            public void onDeleteProfile() {
                profileUi.onDeleteProfile();
            }

            @Override
            public void onProfileSelected() {
                profileUi.onProfileSelected();
            }

            @Override
            public void onRefreshHistory() {
                refreshRouteHistory.run();
            }

            @Override
            public void onAbout() {
                AppMenuDialogs.showAbout(dialogOwner.get());
            }

            @Override
            public void onHelp() {
                AppMenuDialogs.showHelp(dialogOwner.get());
            }

            @Override
            public void onPersistenceSettings() {
                settingsDialogs.onPersistenceSettings();
            }

            @Override
            public void onProfileParamsSettings() {
                settingsDialogs.onProfileParamsSettings();
            }

            @Override
            public void onAlertsSettings() {
                settingsDialogs.onAlertsSettings();
            }

            @Override
            public void onTelemetrySettings() {
                settingsDialogs.onTelemetrySettings();
            }

            @Override
            public void onExportNow() {
                settingsDialogs.onExportNow();
            }

            @Override
            public void onLanguageSelected(UiLocale locale) {
                applyUiLocale.accept(locale);
            }
        };
    }
}
