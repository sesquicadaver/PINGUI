package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostEntry;
import io.pingui.config.PingExpertEntry;
import io.pingui.monitor.MonitorFixtures;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;

class HostListPresenterTest {
    @Test
    void refreshTagChipsBuildsAllAndUniqueTags() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness =
                    new Harness(List.of(tagged("8.8.8.8", "dc", "vpn"), tagged("1.1.1.1", "dc"), tagged("9.9.9.9")));
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);

            FlowPane chips = harness.chipPane();
            assertEquals(3, chips.getChildren().size());
            assertEquals(
                    HostListPresenter.TAG_FILTER_ALL,
                    ((ToggleButton) chips.getChildren().get(0)).getText());
            assertEquals("dc", ((ToggleButton) chips.getChildren().get(1)).getText());
            assertEquals("vpn", ((ToggleButton) chips.getChildren().get(2)).getText());
            assertNull(harness.presenter.activeFilterTag());
            assertEquals(3, harness.presenter.visibleHostCount());
        });
    }

    @Test
    void selectFilterChipHidesHostsWithoutTag() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness(List.of(tagged("8.8.8.8", "dc"), tagged("1.1.1.1", "vpn")));
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);

            harness.presenter.selectFilterChip("dc");
            assertEquals("dc", harness.presenter.activeFilterTag());
            assertEquals(1, harness.presenter.visibleHostCount());
            assertEquals("8.8.8.8", harness.hostList.getItems().get(0).getHost());
        });
    }

    @Test
    void ensureHostVisibleClearsFilterWhenHostHidden() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness(List.of(tagged("8.8.8.8", "dc"), tagged("1.1.1.1", "vpn")));
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);
            harness.presenter.selectFilterChip("dc");
            assertEquals(1, harness.presenter.visibleHostCount());

            harness.presenter.ensureHostVisibleForTagFilter("1.1.1.1");
            assertNull(harness.presenter.activeFilterTag());
            assertEquals(2, harness.presenter.visibleHostCount());
        });
    }

    @Test
    void editSelectedHostTagsPersistsAndKeepsSelectionVisible() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness(List.of(tagged("8.8.8.8", "dc"), tagged("1.1.1.1", "vpn")));
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);
            harness.presenter.selectFilterChip("dc");
            harness.hostList.getSelectionModel().select(0);

            harness.presenter.setTagsEditor((host, current) -> Optional.of(List.of("other")));
            harness.presenter.editSelectedHostTags();

            assertEquals(List.of("other"), harness.store.getTags("8.8.8.8"));
            assertNull(harness.presenter.activeFilterTag());
            assertEquals(2, harness.presenter.visibleHostCount());
            assertEquals(
                    "8.8.8.8",
                    harness.hostList.getSelectionModel().getSelectedItem().getHost());
            assertTrue(harness.infos.stream().anyMatch(line -> line.contains("Теги [8.8.8.8]")));
            assertTrue(harness.errors.isEmpty());
        });
    }

    @Test
    void addHostValidationFailureCallsErrorFeedback() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness(List.of(tagged("8.8.8.8", "dc")));
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);
            harness.hostInput.setText("8.8.8.8");
            harness.presenter.addHost();
            assertTrue(harness.errors.stream().anyMatch(line -> line.contains("Не вдалося додати ціль")));
            assertTrue(harness.infos.isEmpty());
        });
    }

    @Test
    void removeHostCancelDoesNotMutate() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Harness harness = new Harness(List.of(tagged("8.8.8.8"), tagged("1.1.1.1")));
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);
            harness.hostList.getSelectionModel().select(0);
            harness.presenter.setConfirmDeleteHost(host -> false);

            harness.presenter.removeHost();

            assertEquals(2, harness.hostItems.size());
            assertTrue(harness.store.containsHost("8.8.8.8"));
            assertTrue(harness.infos.isEmpty());
            assertTrue(harness.errors.isEmpty());
        });
    }

    @Test
    void removeHostOkDeletesSelectedHost() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            MonitorService monitor = MonitorFixtures.idle();
            monitor.addHost("8.8.8.8", true);
            monitor.addHost("1.1.1.1", true);
            Harness harness = new Harness(List.of(tagged("8.8.8.8"), tagged("1.1.1.1")), () -> monitor);
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);
            harness.hostList.getSelectionModel().select(0);
            harness.presenter.setConfirmDeleteHost(host -> true);

            harness.presenter.removeHost();

            assertEquals(1, harness.hostItems.size());
            assertEquals("1.1.1.1", harness.hostItems.get(0).getHost());
            assertTrue(!harness.store.containsHost("8.8.8.8"));
            assertTrue(harness.infos.stream().anyMatch(line -> line.contains("Видалено ціль: 8.8.8.8")));
            assertEquals(1, harness.dirtyMarks.get());
            monitor.close();
        });
    }

    @Test
    void addHostMarksDirtyOnSuccess() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            MonitorService monitor = MonitorFixtures.idle();
            Harness harness = new Harness(List.of(tagged("8.8.8.8")), () -> monitor);
            harness.presenter.configure();
            harness.presenter.rebuild(harness.entries);
            harness.hostInput.setText("1.1.1.1");

            harness.presenter.addHost();

            assertTrue(harness.store.containsHost("1.1.1.1"));
            assertEquals(1, harness.dirtyMarks.get());
            monitor.close();
        });
    }

    private static HostEntry tagged(String host, String... tags) {
        return new HostEntry(host, true, false, PingExpertEntry.empty(), null, null, List.of(tags));
    }

    private static final class Harness {
        final List<HostEntry> entries;
        final ObservableList<HostItem> hostItems = FXCollections.observableArrayList();
        final ListView<HostItem> hostList = new ListView<>();
        final TextField hostInput = new TextField();
        final SessionStore store;
        final List<String> infos = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final AtomicInteger dirtyMarks = new AtomicInteger();
        final HostListPresenter presenter;

        Harness(List<HostEntry> entries) {
            this(entries, () -> {
                throw new UnsupportedOperationException("monitor unused");
            });
        }

        Harness(List<HostEntry> entries, Supplier<MonitorService> monitor) {
            this.entries = entries;
            this.store = SessionStore.fromEntries(entries);
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
            this.presenter = new HostListPresenter(
                    hostItems,
                    hostList,
                    hostInput,
                    () -> store,
                    monitor,
                    new SimpleBooleanProperty(false),
                    feedback,
                    () -> {},
                    () -> {},
                    () -> {},
                    (oldHost, newHost) -> {},
                    () -> {},
                    () -> {},
                    Runnable::run);
            this.presenter.setMarkDirty(dirtyMarks::incrementAndGet);
        }

        FlowPane chipPane() {
            HBox bar = (HBox) presenter.tagFilterBar();
            return (FlowPane) bar.getChildren().get(1);
        }
    }
}
