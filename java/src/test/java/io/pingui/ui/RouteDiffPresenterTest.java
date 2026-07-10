package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteDiffPresenterTest {

    @Test
    void showPopulatesListAndHeader() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            RouteDiffPresenter presenter = new RouteDiffPresenter();
            presenter.show(
                    List.of(new HopNode(1, "10.0.0.1", 5.0, false)),
                    List.of(new HopNode(1, "192.168.1.1", 8.0, false)));
            assertEquals(1, presenter.listView().getItems().size());
            assertTrue(presenter.panel().getChildren().size() >= 2);
            assertTrue(((javafx.scene.control.Label)
                            presenter.panel().getChildren().get(0))
                    .getText()
                    .contains("1 змін"));
        });
    }

    @Test
    void clearEmptiesList() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            RouteDiffPresenter presenter = new RouteDiffPresenter();
            presenter.show(List.of(new HopNode(1, "10.0.0.1", 1.0, false)), List.of());
            presenter.clear();
            assertTrue(presenter.listView().getItems().isEmpty());
        });
    }

    @Test
    void emptyPreviousDoesNotListFalseAddedHops() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            RouteDiffPresenter presenter = new RouteDiffPresenter();
            presenter.show(List.of(), List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
            assertTrue(presenter.listView().getItems().isEmpty());
            assertTrue(((javafx.scene.control.Label)
                            presenter.panel().getChildren().get(0))
                    .getText()
                    .contains("початковий"));
        });
    }
}
