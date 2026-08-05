package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

class ViewModeControllerTest {
    @Test
    void statusLabelRemainsVisibleInSimpleMode() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label status = new Label(EmptyStateHints.waitingForData());
            TextArea log = new TextArea();
            ViewModeController controller = newController(log, status);
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
            ViewModeController controller = newController(log, status);
            controller.apply();
            assertEquals("Додано ціль: 8.8.8.8", status.getText());
        });
    }

    @Test
    void statusLabelRemainsVisibleInExtendedMode() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label status = new Label("status");
            TextArea log = new TextArea();
            ViewModeController controller = newController(log, status);
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
            BorderPane root = new BorderPane();
            SplitPane split = new SplitPane();
            Label status = new Label(EmptyStateHints.waitingForData());
            TextArea log = new TextArea();
            ViewModeController controller = new ViewModeController(
                    graphPanel, leftPanel, root, split, log, status, () -> {}, () -> {}, () -> false);

            Stage stage = new Stage();
            stage.setScene(new Scene(root, 900, 600));
            stage.setWidth(900);
            stage.setHeight(600);

            double widthBefore = stage.getWidth();
            double heightBefore = stage.getHeight();

            controller.apply();
            assertEquals(widthBefore, stage.getWidth(), 0.5);
            assertEquals(heightBefore, stage.getHeight(), 0.5);

            controller.forceExtended(() -> null);
            controller.apply();
            assertEquals(widthBefore, stage.getWidth(), 0.5);
            assertEquals(heightBefore, stage.getHeight(), 0.5);

            controller.restoreMode(UiViewMode.SIMPLE, () -> null, () -> null);
            controller.apply();
            assertEquals(widthBefore, stage.getWidth(), 0.5);
            assertEquals(heightBefore, stage.getHeight(), 0.5);
        });
    }

    @Test
    void extendedUsesSplitPaneAndDividerRoundTripWithoutShow() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            VBox graphPanel = new VBox();
            VBox leftPanel = new VBox();
            BorderPane root = new BorderPane();
            SplitPane split = new SplitPane();
            Label status = new Label("status");
            TextArea log = new TextArea();
            ViewModeController controller = new ViewModeController(
                    graphPanel, leftPanel, root, split, log, status, () -> {}, () -> {}, () -> false);

            new Scene(new StackPane(root), 1000, 700);
            root.resize(1000, 700);

            controller.forceExtended(() -> null);
            controller.apply();
            assertSame(split, root.getCenter());
            assertEquals(2, split.getItems().size());
            controller.applyDivider(0.4);
            root.layout();
            split.layout();
            assertEquals(0.4, controller.dividerForSave(), 0.02);

            controller.restoreMode(UiViewMode.SIMPLE, () -> null, () -> null);
            controller.apply();
            assertSame(leftPanel, root.getCenter());
            assertEquals(0.4, controller.lastKnownDivider(), 0.02);
            assertEquals(0.4, controller.dividerForSave(), 0.02);
        });
    }

    @Test
    void restoreExtendedModeBeforeApplyUsesSplitPane() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            VBox graphPanel = new VBox();
            VBox leftPanel = new VBox();
            BorderPane root = new BorderPane();
            SplitPane split = new SplitPane();
            ViewModeController controller = new ViewModeController(
                    graphPanel,
                    leftPanel,
                    root,
                    split,
                    new TextArea(),
                    new Label("s"),
                    () -> {},
                    () -> {},
                    () -> false);
            controller.restoreMode(UiViewMode.EXTENDED, () -> null, () -> null);
            controller.apply();
            assertTrue(controller.isExtended());
            assertSame(split, root.getCenter());
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

    private static ViewModeController newController(TextArea log, Label status) {
        return new ViewModeController(
                new VBox(),
                new VBox(),
                new BorderPane(),
                new SplitPane(),
                log,
                status,
                () -> {},
                () -> {},
                () -> false);
    }
}
