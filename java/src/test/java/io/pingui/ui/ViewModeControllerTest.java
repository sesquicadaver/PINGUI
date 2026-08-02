package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

class ViewModeControllerTest {
    @Test
    void statusLabelRemainsVisibleInSimpleMode() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label status = new Label(EmptyStateHints.waitingForData());
            TextArea log = new TextArea();
            ViewModeController controller = new ViewModeController(
                    new VBox(), new VBox(), new BorderPane(), log, status, () -> {}, () -> {}, () -> false);
            controller.apply();
            assertTrue(status.isVisible());
            assertTrue(status.isManaged());
            assertTrue(!log.isVisible());
            assertTrue(!log.isManaged());
            assertEquals(EmptyStateHints.simpleNoLog(), status.getText());
        });
    }

    @Test
    void simpleModeKeepsLiveFeedbackStatus() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label status = new Label("Додано ціль: 8.8.8.8");
            TextArea log = new TextArea();
            ViewModeController controller = new ViewModeController(
                    new VBox(), new VBox(), new BorderPane(), log, status, () -> {}, () -> {}, () -> false);
            controller.apply();
            assertEquals("Додано ціль: 8.8.8.8", status.getText());
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
            assertEquals("status", status.getText());
        });
    }

    @Test
    void applyDoesNotChangeStageSizeAcrossModeToggle() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            VBox graphPanel = new VBox();
            VBox leftPanel = new VBox();
            BorderPane root = new BorderPane(null, null, null, null, leftPanel);
            Label status = new Label(EmptyStateHints.waitingForData());
            TextArea log = new TextArea();
            ViewModeController controller =
                    new ViewModeController(graphPanel, leftPanel, root, log, status, () -> {}, () -> {}, () -> false);

            // Attach a Stage without show() — Monocle can stall later FX pulses after Stage.show().
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 900, 600));
            stage.setWidth(900);
            stage.setHeight(600);

            double widthBefore = stage.getWidth();
            double heightBefore = stage.getHeight();

            controller.apply(); // Simple
            assertEquals(widthBefore, stage.getWidth(), 0.5);
            assertEquals(heightBefore, stage.getHeight(), 0.5);

            controller.forceExtended(() -> null);
            controller.apply();
            assertEquals(widthBefore, stage.getWidth(), 0.5);
            assertEquals(heightBefore, stage.getHeight(), 0.5);

            // Restore Simple without RadioButton wiring (force mode field via restore + apply).
            controller.restoreMode(UiViewMode.SIMPLE, () -> null, () -> null);
            controller.apply();
            assertEquals(widthBefore, stage.getWidth(), 0.5);
            assertEquals(heightBefore, stage.getHeight(), 0.5);
        });
    }

    @Test
    void sourceHasNoWindowResizeOrForcedLayout() throws Exception {
        Path src = Path.of("src/main/java/io/pingui/ui/ViewModeController.java");
        String text = Files.readString(src, StandardCharsets.UTF_8);
        assertFalse(text.contains("fitWindowToContent"), "fitWindowToContent must stay removed");
        assertFalse(text.contains("applyCss()"), "view-mode path must not force applyCss");
        assertFalse(text.contains(".layout()"), "view-mode path must not force layout()");
        assertFalse(text.contains("setWidth("), "ViewModeController must not resize the window");
        assertFalse(text.contains("setHeight("), "ViewModeController must not resize the window");
        assertFalse(text.contains("EXTENDED_WIDTH"), "hardcoded Extended resize constants must stay removed");
        assertFalse(text.contains("EXTENDED_HEIGHT"), "hardcoded Extended resize constants must stay removed");
    }
}
