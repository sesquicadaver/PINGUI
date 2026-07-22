package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostEntry;
import io.pingui.config.PingExpertEntry;
import io.pingui.monitor.RouteChangeEvent;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteHistoryPresenterTest {
    @TempDir
    Path tempDir;

    @Test
    void onRouteChangedIgnoresOtherHosts() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (PresenterHarness harness = new PresenterHarness(tempDir.resolve("a.db"))) {
                harness.presenter.configure();
                harness.filter.setValue("8.8.8.8");
                assertEquals(1, harness.historyList.getItems().size());
                harness.presenter.onRouteChanged("1.1.1.1");
                assertEquals(1, harness.historyList.getItems().size());
            }
        });
    }

    @Test
    void onRouteChangedRefreshesFilteredHostWithoutClearingSelection() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (PresenterHarness harness = new PresenterHarness(tempDir.resolve("b.db"))) {
                harness.presenter.configure();
                harness.filter.setValue("8.8.8.8");
                harness.presenter.refresh();
                harness.historyList.getSelectionModel().select(0);
                assertEquals(1, harness.replayCount.get());

                harness.insertEvent(
                        "8.8.8.8",
                        List.of("1.0.0.1"),
                        List.of("9.9.9.9"),
                        Instant.now().minusSeconds(30));
                harness.presenter.onRouteChanged("8.8.8.8");

                assertEquals(2, harness.historyList.getItems().size());
                assertFalse(harness.historyList.getSelectionModel().isEmpty());
            }
        });
    }

    @Test
    void filterChangeClearsReplay() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (PresenterHarness harness = new PresenterHarness(tempDir.resolve("c.db"))) {
                harness.presenter.configure();
                harness.filter.setValue("8.8.8.8");
                harness.presenter.refresh();
                harness.historyList.getSelectionModel().select(0);
                assertEquals(1, harness.replayCount.get());

                harness.filter.setValue("1.1.1.1");
                assertTrue(harness.clearReplayCount.get() >= 1);
                assertNull(harness.historyList.getSelectionModel().getSelectedItem());
            }
        });
    }

    @Test
    void reloadKeepingFilterPreservesSelectionWhenRowStillExists() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (PresenterHarness harness = new PresenterHarness(tempDir.resolve("d.db"))) {
                harness.presenter.configure();
                harness.filter.setValue("8.8.8.8");
                harness.presenter.refresh();
                long selectedId = harness.historyList.getItems().get(0).id();
                harness.historyList.getSelectionModel().select(0);

                harness.insertEvent(
                        "8.8.8.8",
                        List.of("1.0.0.1"),
                        List.of("8.8.4.4"),
                        Instant.now().minusSeconds(15));
                harness.presenter.reloadKeepingFilter();

                assertEquals(2, harness.historyList.getItems().size());
                assertEquals(
                        selectedId,
                        harness.historyList
                                .getSelectionModel()
                                .getSelectedItem()
                                .id());
            }
        });
    }

    @Test
    void rebuildHostFilterPreservesActiveHostWhenSecondHostAdded() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (PresenterHarness harness = new PresenterHarness(tempDir.resolve("e.db"))) {
                harness.presenter.configure();
                harness.filter.setValue("8.8.8.8");
                harness.presenter.refresh();
                assertEquals(1, harness.historyList.getItems().size());
                assertEquals(
                        "8.8.8.8", harness.historyList.getItems().get(0).event().host());

                harness.presenter.rebuildHostFilter(List.of("8.8.8.8", "1.1.1.1"));

                assertEquals("8.8.8.8", harness.filter.getValue());
                assertEquals(1, harness.historyList.getItems().size());
                assertEquals(
                        "8.8.8.8", harness.historyList.getItems().get(0).event().host());
            }
        });
    }

    @Test
    void placeholderShowsEmptyHistoryWhenNoEventsForHost() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (PresenterHarness harness = new PresenterHarness(tempDir.resolve("empty.db"))) {
                harness.presenter.configure();
                harness.filter.setValue("8.8.8.8");
                // Clear seeded events by using a host with no rows.
                harness.filter.setValue("9.9.9.9");
                harness.presenter.refresh();
                assertTrue(harness.historyList.getItems().isEmpty());
                assertEquals(EmptyStateHints.emptyHistory(), harness.presenter.placeholderText());
            }
        });
    }

    @Test
    void placeholderShowsNoHostWhenFilterEmpty() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try (PresenterHarness harness = new PresenterHarness(tempDir.resolve("nohost.db"))) {
                harness.presenter.configure();
                harness.filter.setValue(null);
                harness.presenter.refresh();
                assertEquals(EmptyStateHints.noHostSelected(), harness.presenter.placeholderText());
            }
        });
    }

    @Test
    void placeholderShowsNoSqliteWithoutPersistence() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            ComboBox<String> filter = new ComboBox<>();
            ListView<RouteHistoryItem> historyList = new ListView<>();
            SessionStore ramStore = SessionStore.fromEntries(
                    List.of(new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty())), null);
            RouteHistoryPresenter presenter = new RouteHistoryPresenter(
                    () -> ramStore,
                    filter,
                    historyList,
                    new RadioButton("24 год"),
                    new RadioButton("7 днів"),
                    () -> true,
                    event -> {},
                    () -> {});
            presenter.configure();
            presenter.refresh();
            assertFalse(ramStore.hasPersistence());
            assertEquals(EmptyStateHints.noSqlite(), presenter.placeholderText());
            ramStore.close();
        });
    }

    private static final class PresenterHarness implements AutoCloseable {
        final ComboBox<String> filter = new ComboBox<>();
        final ListView<RouteHistoryItem> historyList = new ListView<>();
        final RadioButton range24h = new RadioButton("24 год");
        final RadioButton range7d = new RadioButton("7 днів");
        final AtomicInteger replayCount = new AtomicInteger();
        final AtomicInteger clearReplayCount = new AtomicInteger();
        final SessionStore store;
        final RouteHistoryPresenter presenter;
        final SessionDatabase database;

        PresenterHarness(Path dbPath) {
            database = new SessionDatabase(dbPath);
            store = SessionStore.fromEntries(
                    List.of(
                            new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty()),
                            new HostEntry("1.1.1.1", true, false, PingExpertEntry.empty())),
                    database);
            presenter = new RouteHistoryPresenter(
                    () -> store,
                    filter,
                    historyList,
                    range24h,
                    range7d,
                    () -> true,
                    event -> replayCount.incrementAndGet(),
                    () -> clearReplayCount.incrementAndGet());
            // Relative times stay inside the default 24h lookback regardless of wall clock.
            insertEvent("8.8.8.8", List.of(), List.of("1.1.1.1"), Instant.now().minus(Duration.ofHours(2)));
            insertEvent("1.1.1.1", List.of(), List.of("8.8.8.8"), Instant.now().minus(Duration.ofHours(1)));
        }

        void insertEvent(String host, List<String> oldIps, List<String> newIps, Instant timestamp) {
            RouteChangeEvent event = RouteChangeEvent.fromRouteChange(host, oldIps, newIps, "default", timestamp);
            database.insertEvent(PersistenceEventType.ROUTE_CHANGE, host, "default", event.toJson(), timestamp);
        }

        @Override
        public void close() {
            store.close();
        }
    }
}
