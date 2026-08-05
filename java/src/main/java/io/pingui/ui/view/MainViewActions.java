package io.pingui.ui.view;

import io.pingui.i18n.UiLocale;

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

    /** Runtime UI locale change (P25). */
    void onLanguageSelected(UiLocale locale);
}
