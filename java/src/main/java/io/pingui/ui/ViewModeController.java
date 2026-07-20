package io.pingui.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Simple vs extended layout and window sizing. */
final class ViewModeController {
    private static final double SIMPLE_PANEL_MIN_WIDTH = 580.0;
    private static final double EXTENDED_WIDTH = 1100.0;
    private static final double EXTENDED_HEIGHT = 700.0;

    private UiViewMode viewMode = UiViewMode.SIMPLE;
    private final VBox graphPanel;
    private final VBox leftPanel;
    private final BorderPane root;
    private final TextArea logArea;
    private final Label statusLabel;
    private final Runnable redrawRoute;
    private final Runnable showEasterEggCanvas;
    private final BooleanSupplier easterEggActive;

    ViewModeController(
            VBox graphPanel,
            VBox leftPanel,
            BorderPane root,
            TextArea logArea,
            Label statusLabel,
            Runnable redrawRoute,
            Runnable showEasterEggCanvas,
            BooleanSupplier easterEggActive) {
        this.graphPanel = graphPanel;
        this.leftPanel = leftPanel;
        this.root = root;
        this.logArea = logArea;
        this.statusLabel = statusLabel;
        this.redrawRoute = redrawRoute;
        this.showEasterEggCanvas = showEasterEggCanvas;
        this.easterEggActive = easterEggActive;
    }

    UiViewMode viewMode() {
        return viewMode;
    }

    boolean isExtended() {
        return viewMode == UiViewMode.EXTENDED;
    }

    void onToggleSelected(Toggle toggle) {
        if (toggle == null) {
            return;
        }
        viewMode = ((RadioButton) toggle).getText().equals("Розширений") ? UiViewMode.EXTENDED : UiViewMode.SIMPLE;
        apply();
    }

    void apply() {
        boolean extended = viewMode == UiViewMode.EXTENDED;
        graphPanel.setVisible(extended);
        graphPanel.setManaged(extended);
        logArea.setVisible(extended);
        logArea.setManaged(extended);
        // P20-001: status stays visible in Simple (operator feedback) and Extended (live tick).
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        root.setCenter(extended ? graphPanel : null);
        BorderPane.setMargin(leftPanel, extended ? new Insets(0, 4, 0, 0) : Insets.EMPTY);
        if (extended) {
            leftPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            if (easterEggActive.getAsBoolean()) {
                showEasterEggCanvas.run();
            } else {
                redrawRoute.run();
            }
        } else {
            leftPanel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            root.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        }
        fitWindowToContent();
    }

    void fitWindowToContent() {
        Platform.runLater(() -> {
            Scene scene = root.getScene();
            if (scene == null || scene.getWindow() == null) {
                return;
            }
            if (viewMode == UiViewMode.SIMPLE) {
                leftPanel.applyCss();
                leftPanel.layout();
                root.applyCss();
                root.layout();
                double prefW = Math.max(SIMPLE_PANEL_MIN_WIDTH, root.prefWidth(-1));
                double prefH = Math.max(root.minHeight(-1), root.prefHeight(-1));
                scene.getWindow().setWidth(prefW);
                scene.getWindow().setHeight(prefH);
            } else {
                scene.getWindow().setWidth(EXTENDED_WIDTH);
                scene.getWindow().setHeight(EXTENDED_HEIGHT);
            }
        });
    }

    void restoreMode(UiViewMode mode, Supplier<RadioButton> simpleButton, Supplier<RadioButton> extendedButton) {
        viewMode = mode;
        if (mode == UiViewMode.SIMPLE) {
            RadioButton simple = simpleButton.get();
            if (simple != null) {
                simple.setSelected(true);
            }
        } else {
            RadioButton extended = extendedButton.get();
            if (extended != null) {
                extended.setSelected(true);
            }
        }
    }

    void forceExtended(Supplier<RadioButton> extendedButton) {
        viewMode = UiViewMode.EXTENDED;
        RadioButton extended = extendedButton.get();
        if (extended != null) {
            extended.setSelected(true);
        }
    }
}
