package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostEntry;
import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.RouteChangeEvent;
import io.pingui.monitor.SessionStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteGraphPresenterTest {

    @Test
    void replayClearsOnClearReplay() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas canvas = new GraphCanvas();
            RouteDiffPresenter diff = new RouteDiffPresenter();
            var items = javafx.collections.FXCollections.observableArrayList(new HostItem("8.8.8.8", true));
            javafx.scene.control.ListView<HostItem> hostList = new javafx.scene.control.ListView<>(items);
            hostList.getSelectionModel().select(0);
            SessionStore store =
                    SessionStore.fromEntries(List.of(new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty())));

            RouteGraphPresenter presenter =
                    new RouteGraphPresenter(canvas, hostList, () -> store, () -> true, () -> false, diff);
            RouteChangeEvent event = RouteChangeEvent.fromRouteChange(
                    "8.8.8.8", List.of("1.1.1.1"), List.of("8.8.8.8"), "default", java.time.Instant.now());

            presenter.replayRouteChange(event);
            assertTrue(presenter.isReplaying());
            assertFalse(diff.listView().getItems().isEmpty());

            presenter.clearReplay();
            assertFalse(presenter.isReplaying());
        });
    }

    @Test
    void liveRedrawShowsDiffAgainstPreviousRoute() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas canvas = new GraphCanvas();
            RouteDiffPresenter diff = new RouteDiffPresenter();
            var items = javafx.collections.FXCollections.observableArrayList(new HostItem("8.8.8.8", true));
            javafx.scene.control.ListView<HostItem> hostList = new javafx.scene.control.ListView<>(items);
            hostList.getSelectionModel().select(0);
            SessionStore store =
                    SessionStore.fromEntries(List.of(new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty())));
            store.updateRoute(
                    "8.8.8.8",
                    new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false))));
            store.updateRoute(
                    "8.8.8.8",
                    new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "192.168.1.1", 7.0, false))));

            RouteGraphPresenter presenter =
                    new RouteGraphPresenter(canvas, hostList, () -> store, () -> true, () -> false, diff);
            presenter.redrawIfExtended();

            assertEquals(1, diff.listView().getItems().size());
            assertEquals(
                    RouteDiff.Kind.CHANGED, diff.listView().getItems().get(0).kind());
            assertTrue(diff.listView().getItems().get(0).summary().contains("→"));
        });
    }
}
