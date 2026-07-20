package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.TracingProfile;
import io.pingui.monitor.SessionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.Test;

class ProfileUiCoordinatorTest {
    @Test
    void onDeleteProfileCancelDoesNotMutate() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness();
            harness.coordinator.setConfirmDeleteProfile(name -> false);

            harness.coordinator.onDeleteProfile();

            assertTrue(harness.document.hasProfile("lab"));
            assertEquals("lab", harness.document.activeProfile());
            assertEquals(0, harness.reloadCount.get());
            assertTrue(harness.infos.isEmpty());
            assertTrue(harness.errors.isEmpty());
        });
    }

    @Test
    void onDeleteProfileOkRemovesActiveProfile() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness();
            harness.coordinator.setConfirmDeleteProfile(name -> true);

            harness.coordinator.onDeleteProfile();

            assertTrue(!harness.document.hasProfile("lab"));
            assertEquals("default", harness.document.activeProfile());
            assertEquals(1, harness.reloadCount.get());
            assertTrue(harness.infos.stream().anyMatch(line -> line.contains("Видалено профіль: lab")));
            assertTrue(harness.dirty.get());
        });
    }

    @Test
    void onProfileSelectedCancelKeepsActiveWhenDirty() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness();
            harness.dirty.set(true);
            harness.unsavedDecision.set(ConfirmDialogs.UnsavedDecision.CANCEL);
            harness.profileCombo.getSelectionModel().select("default");

            harness.coordinator.onProfileSelected();

            assertEquals("lab", harness.document.activeProfile());
            assertEquals("lab", harness.profileCombo.getSelectionModel().getSelectedItem());
            assertEquals(0, harness.reloadCount.get());
            assertEquals(0, harness.saveCalls.get());
        });
    }

    @Test
    void onProfileSelectedSaveThenSwitches() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness();
            harness.dirty.set(true);
            harness.unsavedDecision.set(ConfirmDialogs.UnsavedDecision.SAVE);
            harness.saveResult.set(true);
            harness.profileCombo.getSelectionModel().select("default");

            harness.coordinator.onProfileSelected();

            assertEquals("default", harness.document.activeProfile());
            assertEquals(1, harness.saveCalls.get());
            assertEquals(1, harness.reloadCount.get());
            assertTrue(harness.infos.stream().anyMatch(line -> line.contains("Завантажено профіль: default")));
        });
    }

    @Test
    void onProfileSelectedSaveFailureRevertsCombo() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness();
            harness.dirty.set(true);
            harness.unsavedDecision.set(ConfirmDialogs.UnsavedDecision.SAVE);
            harness.saveResult.set(false);
            harness.profileCombo.getSelectionModel().select("default");

            harness.coordinator.onProfileSelected();

            assertEquals("lab", harness.document.activeProfile());
            assertEquals("lab", harness.profileCombo.getSelectionModel().getSelectedItem());
            assertEquals(1, harness.saveCalls.get());
            assertEquals(0, harness.reloadCount.get());
        });
    }

    @Test
    void onProfileSelectedDiscardSwitchesWithoutSave() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness();
            harness.dirty.set(true);
            harness.unsavedDecision.set(ConfirmDialogs.UnsavedDecision.DISCARD);
            harness.profileCombo.getSelectionModel().select("default");

            harness.coordinator.onProfileSelected();

            assertEquals("default", harness.document.activeProfile());
            assertEquals(0, harness.saveCalls.get());
            assertEquals(1, harness.reloadCount.get());
            assertTrue(harness.dirty.get());
        });
    }

    private static final class Harness {
        final ProfileDocument document;
        final SessionStore store;
        final ComboBox<String> profileCombo = new ComboBox<>();
        final AtomicBoolean switching = new AtomicBoolean(false);
        final AtomicInteger reloadCount = new AtomicInteger();
        final AtomicInteger saveCalls = new AtomicInteger();
        final AtomicBoolean saveResult = new AtomicBoolean(true);
        final AtomicBoolean dirty = new AtomicBoolean(false);
        final AtomicReference<ConfirmDialogs.UnsavedDecision> unsavedDecision =
                new AtomicReference<>(ConfirmDialogs.UnsavedDecision.DISCARD);
        final List<String> infos = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final ProfileUiCoordinator coordinator;

        Harness() {
            TracingProfile lab = TracingProfile.defaults(List.of(HostEntry.basic("8.8.8.8", true)));
            TracingProfile defaults = TracingProfile.defaults(List.of());
            this.document = new ProfileDocument("lab", Map.of("lab", lab, "default", defaults));
            this.store = SessionStore.fromEntries(lab.hosts());
            UserFeedback feedback = new UserFeedback() {
                @Override
                public void info(String message) {
                    infos.add(message);
                }

                @Override
                public void error(String message) {
                    errors.add(message);
                }
            };
            this.coordinator = new ProfileUiCoordinator(
                    () -> document,
                    () -> store,
                    profileCombo,
                    switching::get,
                    switching::set,
                    reloadCount::incrementAndGet,
                    () -> profileCombo.setItems(FXCollections.observableArrayList(
                            document.profiles().keySet())),
                    feedback);
            coordinator.setDirtyHooks(
                    () -> dirty.set(true),
                    dirty::get,
                    () -> {
                        saveCalls.incrementAndGet();
                        if (saveResult.get()) {
                            dirty.set(false);
                        }
                        return saveResult.get();
                    },
                    unsavedDecision::get);
            profileCombo.setItems(
                    FXCollections.observableArrayList(document.profiles().keySet()));
            profileCombo.getSelectionModel().select("lab");
        }
    }
}
