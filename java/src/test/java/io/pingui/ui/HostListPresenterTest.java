package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostEntry;
import io.pingui.config.PingExpertEntry;
import io.pingui.monitor.SessionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            assertTrue(harness.logs.stream().anyMatch(line -> line.contains("Теги [8.8.8.8]")));
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
        final List<String> logs = new ArrayList<>();
        final HostListPresenter presenter;

        Harness(List<HostEntry> entries) {
            this.entries = entries;
            this.store = SessionStore.fromEntries(entries);
            this.presenter = new HostListPresenter(
                    hostItems,
                    hostList,
                    hostInput,
                    () -> store,
                    () -> {
                        throw new UnsupportedOperationException("monitor unused");
                    },
                    new SimpleBooleanProperty(false),
                    logs::add,
                    () -> {},
                    () -> {},
                    () -> {},
                    (oldHost, newHost) -> {},
                    () -> {},
                    () -> {},
                    Runnable::run);
        }

        FlowPane chipPane() {
            HBox bar = (HBox) presenter.tagFilterBar();
            return (FlowPane) bar.getChildren().get(1);
        }
    }
}
