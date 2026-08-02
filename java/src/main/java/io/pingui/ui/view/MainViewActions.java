package io.pingui.ui.view;

/**
 * Chrome → orchestration callbacks for {@link MainView}. Business logic stays in {@code
 * MainController}.
 */
public interface MainViewActions {
    void onSaveConfig();

    void onAddHost();

    void onEditHost();

    void onEditTags();

    void onRemoveHost();

    void onNewProfile();

    void onDeleteProfile();

    void onProfileSelected();

    void onRefreshHistory();

    void onAbout();

    void onHelp();

    void onPersistenceSettings();

    void onProfileParamsSettings();

    void onAlertsSettings();

    void onTelemetrySettings();

    void onExportNow();
}
