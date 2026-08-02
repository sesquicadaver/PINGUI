package io.pingui.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Simple vs extended layout. Owns SplitPane re-parenting; window size is owned by the Stage /
 * {@link WindowGeometryStore}.
 */
final class ViewModeController {
    private UiViewMode viewMode = UiViewMode.SIMPLE;
    private final VBox graphPanel;
    private final VBox leftPanel;
    private final BorderPane root;
    private final SplitPane mainSplit;
    private final TextArea logArea;
    private final Label statusLabel;
    private final Runnable redrawRoute;
    private final Runnable showEasterEggCanvas;
    private final BooleanSupplier easterEggActive;
    private double lastKnownDivider = WindowGeometry.DEFAULT_DIVIDER;
    private boolean dividerListenerWired;

    ViewModeController(
            VBox graphPanel,
            VBox leftPanel,
            BorderPane root,
            SplitPane mainSplit,
            TextArea logArea,
            Label statusLabel,
            Runnable redrawRoute,
            Runnable showEasterEggCanvas,
            BooleanSupplier easterEggActive) {
        this.graphPanel = graphPanel;
        this.leftPanel = leftPanel;
        this.root = root;
        this.mainSplit = mainSplit;
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

    double lastKnownDivider() {
        return lastKnownDivider;
    }

    /** Divider to persist: live SplitPane position when Extended, else last known. */
    double dividerForSave() {
        if (isExtended() && !mainSplit.getDividers().isEmpty()) {
            lastKnownDivider =
                    WindowGeometry.clampDivider(mainSplit.getDividers().get(0).getPosition());
        }
        return lastKnownDivider;
    }

    void applyDivider(double position) {
        lastKnownDivider = WindowGeometry.clampDivider(position);
        if (isExtended() && mainSplit.getItems().size() == 2) {
            mainSplit.setDividerPositions(lastKnownDivider);
            wireDividerListener();
        }
    }

    void onToggleSelected(Toggle toggle) {
        if (toggle == null) {
            return;
        }
        Object data = toggle.getUserData();
        viewMode = data instanceof UiViewMode mode ? mode : UiViewMode.SIMPLE;
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
        BorderPane.setMargin(leftPanel, extended ? new Insets(0, 4, 0, 0) : Insets.EMPTY);
        if (extended) {
            leftPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            ensureSplitLayout();
            root.setCenter(mainSplit);
            applyDivider(lastKnownDivider);
            if (easterEggActive.getAsBoolean()) {
                showEasterEggCanvas.run();
            } else {
                redrawRoute.run();
            }
        } else {
            leftPanel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            root.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            detachSimpleLayout();
            root.setCenter(leftPanel);
            // P20-007: Simple hides the log — idle status points to Extended (keep live feedback).
            if (EmptyStateHints.isReplaceableSimpleStatus(statusLabel.getText())) {
                statusLabel.setText(EmptyStateHints.simpleNoLog());
            }
        }
    }

    private void ensureSplitLayout() {
        if (mainSplit.getItems().size() == 2
                && mainSplit.getItems().get(0) == leftPanel
                && mainSplit.getItems().get(1) == graphPanel) {
            wireDividerListener();
            return;
        }
        // Detach from BorderPane / previous parent before SplitPane adoption.
        if (root.getCenter() == leftPanel || root.getCenter() == mainSplit) {
            root.setCenter(null);
        }
        if (root.getLeft() == leftPanel) {
            root.setLeft(null);
        }
        mainSplit.getItems().setAll(leftPanel, graphPanel);
        wireDividerListener();
    }

    private void detachSimpleLayout() {
        if (!mainSplit.getItems().isEmpty()) {
            // Capture divider before items (and dividers) disappear.
            if (!mainSplit.getDividers().isEmpty()) {
                lastKnownDivider = WindowGeometry.clampDivider(
                        mainSplit.getDividers().get(0).getPosition());
            }
            mainSplit.getItems().clear();
            dividerListenerWired = false;
        }
        if (root.getLeft() == leftPanel) {
            root.setLeft(null);
        }
    }

    private void wireDividerListener() {
        if (dividerListenerWired || mainSplit.getDividers().isEmpty()) {
            return;
        }
        dividerListenerWired = true;
        mainSplit.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
            if (newPos != null) {
                lastKnownDivider = WindowGeometry.clampDivider(newPos.doubleValue());
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
