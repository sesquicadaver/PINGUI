package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.TracingProfile;
import io.pingui.monitor.SessionStore;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

/** Profile selection and CRUD in the main window. */
final class ProfileUiCoordinator {
    private final Supplier<ProfileDocument> profileDocument;
    private final Supplier<SessionStore> store;
    private final ComboBox<String> profileCombo;
    private final BooleanSupplier switchingProfile;
    private final Consumer<Boolean> setSwitchingProfile;
    private final Runnable reloadActiveProfile;
    private final Runnable refreshProfileCombo;
    private final UserFeedback userFeedback;
    private Function<String, Boolean> confirmDeleteProfile = this::confirmDeleteProfileDialog;

    ProfileUiCoordinator(
            Supplier<ProfileDocument> profileDocument,
            Supplier<SessionStore> store,
            ComboBox<String> profileCombo,
            BooleanSupplier switchingProfile,
            Consumer<Boolean> setSwitchingProfile,
            Runnable reloadActiveProfile,
            Runnable refreshProfileCombo,
            UserFeedback userFeedback) {
        this.profileDocument = profileDocument;
        this.store = store;
        this.profileCombo = profileCombo;
        this.switchingProfile = switchingProfile;
        this.setSwitchingProfile = setSwitchingProfile;
        this.reloadActiveProfile = reloadActiveProfile;
        this.refreshProfileCombo = refreshProfileCombo;
        this.userFeedback = userFeedback;
    }

    /** Package-visible for tests: inject delete confirmation without modal Alert. */
    void setConfirmDeleteProfile(Function<String, Boolean> confirmDeleteProfile) {
        this.confirmDeleteProfile =
                confirmDeleteProfile != null ? confirmDeleteProfile : this::confirmDeleteProfileDialog;
    }

    void syncActiveProfileFromSession() {
        ProfileDocument document = profileDocument.get();
        TracingProfile current = document.active();
        List<HostEntry> hosts = HostViewRules.entriesForConfig(store.get().toHostEntries());
        document.putProfile(
                document.activeProfile(),
                new TracingProfile(
                        current.intervalSeconds(),
                        current.maxHops(),
                        current.timeoutSeconds(),
                        current.probeMode(),
                        current.hostProbeMode(),
                        hosts,
                        current.alerts(),
                        current.persistence(),
                        current.maxConcurrentTraces(),
                        current.telemetry()));
    }

    void refreshCombo() {
        setSwitchingProfile.accept(true);
        ProfileDocument document = profileDocument.get();
        String active = document.activeProfile();
        profileCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                document.profiles().keySet()));
        profileCombo.getSelectionModel().select(active);
        setSwitchingProfile.accept(false);
    }

    void onProfileSelected() {
        if (switchingProfile.getAsBoolean()) {
            return;
        }
        String selected = profileCombo.getSelectionModel().getSelectedItem();
        ProfileDocument document = profileDocument.get();
        if (selected == null || selected.equals(document.activeProfile())) {
            return;
        }
        try {
            syncActiveProfileFromSession();
            document.setActiveProfile(selected);
            reloadActiveProfile.run();
            userFeedback.info("Завантажено профіль: " + selected);
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
            refreshCombo();
        }
    }

    void onNewProfile() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Новий профіль");
        dialog.setHeaderText("Ім'я профілю трасування");
        dialog.setContentText("Назва:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }
        String name = result.get().strip();
        ProfileDocument document = profileDocument.get();
        if (document.hasProfile(name)) {
            userFeedback.error("Профіль уже існує: " + name);
            return;
        }
        try {
            syncActiveProfileFromSession();
            document.putProfile(name, TracingProfile.defaults(List.of()));
            document.setActiveProfile(name);
            reloadActiveProfile.run();
            refreshCombo();
            userFeedback.info("Створено профіль: " + name);
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
        }
    }

    void onDeleteProfile() {
        ProfileDocument document = profileDocument.get();
        String active = document.activeProfile();
        if (!Boolean.TRUE.equals(confirmDeleteProfile.apply(active))) {
            return;
        }
        try {
            syncActiveProfileFromSession();
            document.removeProfile(active);
            reloadActiveProfile.run();
            refreshCombo();
            userFeedback.info("Видалено профіль: " + active);
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
        }
    }

    private boolean confirmDeleteProfileDialog(String profileName) {
        Window owner = profileCombo.getScene() != null ? profileCombo.getScene().getWindow() : null;
        return ConfirmDialogs.confirm(
                owner,
                "Видалити профіль",
                "Видалити профіль «" + profileName + "»?",
                "Профіль зникне з документа. Збережіть конфіг, щоб зміна потрапила у YAML.");
    }
}
