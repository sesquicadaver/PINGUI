package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostEntry;
import io.pingui.config.PingExpertEntry;
import io.pingui.monitor.RouteChangeEvent;
import io.pingui.monitor.SessionStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteGraphPresenterTest {

    @Test
    void replayClearsOnClearReplay() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas canvas = new GraphCanvas();
            var items = javafx.collections.FXCollections.observableArrayList(new HostItem("8.8.8.8", true));
            javafx.scene.control.ListView<HostItem> hostList = new javafx.scene.control.ListView<>(items);
            hostList.getSelectionModel().select(0);
            SessionStore store =
                    SessionStore.fromEntries(List.of(new HostEntry("8.8.8.8", true, false, PingExpertEntry.empty())));

            RouteGraphPresenter presenter =
                    new RouteGraphPresenter(canvas, hostList, () -> store, () -> true, () -> false);
            RouteChangeEvent event = RouteChangeEvent.fromRouteChange(
                    "8.8.8.8", List.of("1.1.1.1"), List.of("8.8.8.8"), "default", java.time.Instant.now());

            presenter.replayRouteChange(event);
            assertTrue(presenter.isReplaying());

            presenter.clearReplay();
            assertFalse(presenter.isReplaying());
        });
    }
}
