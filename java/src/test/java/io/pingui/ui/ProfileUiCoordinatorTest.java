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
        });
    }

    private static final class Harness {
        final ProfileDocument document;
        final SessionStore store;
        final ComboBox<String> profileCombo = new ComboBox<>();
        final AtomicBoolean switching = new AtomicBoolean(false);
        final AtomicInteger reloadCount = new AtomicInteger();
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
            profileCombo.setItems(
                    FXCollections.observableArrayList(document.profiles().keySet()));
            profileCombo.getSelectionModel().select("lab");
        }
    }
}
