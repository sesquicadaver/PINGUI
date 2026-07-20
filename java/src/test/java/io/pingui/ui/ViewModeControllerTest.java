package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

class ViewModeControllerTest {
    @Test
    void statusLabelRemainsVisibleInSimpleMode() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label status = new Label("status");
            TextArea log = new TextArea();
            ViewModeController controller = new ViewModeController(
                    new VBox(), new VBox(), new BorderPane(), log, status, () -> {}, () -> {}, () -> false);
            controller.apply();
            assertTrue(status.isVisible());
            assertTrue(status.isManaged());
            assertTrue(!log.isVisible());
            assertTrue(!log.isManaged());
        });
    }

    @Test
    void statusLabelRemainsVisibleInExtendedMode() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label status = new Label("status");
            TextArea log = new TextArea();
            ViewModeController controller = new ViewModeController(
                    new VBox(), new VBox(), new BorderPane(), log, status, () -> {}, () -> {}, () -> false);
            controller.forceExtended(() -> null);
            controller.apply();
            assertTrue(status.isVisible());
            assertTrue(status.isManaged());
            assertTrue(log.isVisible());
            assertTrue(log.isManaged());
        });
    }
}
