package io.pingui.ui;

import io.pingui.ui.view.MainView;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Stage bounds, Simple fit, Extended expand, and close-time geometry capture. */
final class StageGeometryCoordinator {
    private Stage mainStage;
    private double extendedDefaultWidth = WindowGeometry.DEFAULT_EXTENDED_WIDTH;
    private double extendedDefaultHeight = WindowGeometry.DEFAULT_EXTENDED_HEIGHT;
    private WindowGeometry lastFloatingGeometry;
    private WindowGeometry pendingDividerRestore;

    private final MainView mainView;
    private final Supplier<ViewModeController> viewModeController;
    private final Runnable onSceneShown;

    StageGeometryCoordinator(
            MainView mainView, Supplier<ViewModeController> viewModeController, Runnable onSceneShown) {
        this.mainView = mainView;
        this.viewModeController = viewModeController;
        this.onSceneShown = onSceneShown;
    }

    void prepareStageGeometry(
            Stage stage,
            double defaultWidthSimple,
            double defaultWidthExtended,
            double defaultHeightSimple,
            double defaultHeightExtended) {
        this.mainStage = stage;
        this.extendedDefaultWidth = defaultWidthExtended;
        this.extendedDefaultHeight = defaultHeightExtended;
        WindowGeometryStore store = WindowGeometryStore.userDefault();
        WindowGeometry loaded =
                store.load(defaultWidthSimple, defaultWidthExtended, defaultHeightSimple, defaultHeightExtended);
        Rectangle2D visual = visualBoundsFor(loaded);
        WindowGeometry clamped = loaded.clamp(
                visual.getMinX(),
                visual.getMinY(),
                visual.getWidth(),
                visual.getHeight(),
                defaultWidthSimple,
                defaultHeightSimple);
        boolean resetSize = loaded.viewMode() == UiViewMode.EXTENDED
                || WindowGeometry.fillsVisualBounds(
                        clamped.width(), clamped.height(), visual.getWidth(), visual.getHeight());
        double startW = resetSize ? defaultWidthSimple : clamped.width();
        double startH = resetSize ? defaultHeightSimple : clamped.height();
        WindowGeometry geometry =
                new WindowGeometry(clamped.x(), clamped.y(), startW, startH, clamped.divider(), UiViewMode.SIMPLE);
        lastFloatingGeometry = geometry;
        applyRestoredGeometry(geometry);
        clearStageMaximize(stage);
        if (!Double.isNaN(geometry.x())) {
            stage.setX(geometry.x());
        }
        if (!Double.isNaN(geometry.y())) {
            stage.setY(geometry.y());
        }
        stage.setWidth(geometry.width());
        stage.setHeight(geometry.height());
        pendingDividerRestore = geometry;
        stage.setOnCloseRequest(event -> store.save(captureGeometry(stage)));
    }

    void onStageShown() {
        if (pendingDividerRestore != null) {
            WindowGeometry geometry = pendingDividerRestore;
            pendingDividerRestore = null;
            Platform.runLater(() -> viewModeController.get().applyDivider(geometry.divider()));
        }
        Platform.runLater(() -> {
            fitSimpleStageGeometryIfNeeded();
            Platform.runLater(this::fitSimpleStageGeometryIfNeeded);
        });
        onSceneShown.run();
    }

    void fitSimpleStageGeometryIfNeeded() {
        ViewModeController mode = viewModeController.get();
        if (mainStage == null || mode == null || mode.isExtended()) {
            return;
        }
        clearStageMaximize(mainStage);
        Region root = mainView.root();
        root.applyCss();
        root.layout();
        double nextW = WindowGeometry.fitSimpleWidth(mainStage.getWidth(), root.prefWidth(-1));
        double nextH = WindowGeometry.fitSimpleHeight(mainStage.getHeight(), root.prefHeight(-1));
        if (nextW + 0.5 < mainStage.getWidth()) {
            mainStage.setWidth(nextW);
        }
        if (nextH + 0.5 < mainStage.getHeight()) {
            mainStage.setHeight(nextH);
        }
        rememberFloatingGeometry(mainStage);
    }

    void ensureExtendedStageGeometry() {
        ViewModeController mode = viewModeController.get();
        if (mainStage == null || mode == null || !mode.isExtended()) {
            return;
        }
        clearStageMaximize(mainStage);
        double nextW = WindowGeometry.ensureExtendedWidth(mainStage.getWidth(), extendedDefaultWidth);
        double nextH = WindowGeometry.ensureExtendedHeight(mainStage.getHeight(), extendedDefaultHeight);
        if (nextW > mainStage.getWidth() + 0.5) {
            mainStage.setWidth(nextW);
        }
        if (nextH > mainStage.getHeight() + 0.5) {
            mainStage.setHeight(nextH);
        }
        mode.applyDivider(WindowGeometry.dividerForLeftWidth(mainStage.getWidth(), WindowGeometry.EXTENDED_LEFT_WIDTH));
        rememberFloatingGeometry(mainStage);
    }

    static void clearStageMaximize(Stage stage) {
        if (stage == null) {
            return;
        }
        if (stage.isFullScreen()) {
            stage.setFullScreen(false);
        }
        if (stage.isMaximized()) {
            stage.setMaximized(false);
        }
    }

    private void rememberFloatingGeometry(Stage stage) {
        if (stage == null || stage.isMaximized() || stage.isFullScreen()) {
            return;
        }
        ViewModeController mode = viewModeController.get();
        lastFloatingGeometry = new WindowGeometry(
                stage.getX(),
                stage.getY(),
                stage.getWidth(),
                stage.getHeight(),
                mode != null ? mode.dividerForSave() : WindowGeometry.DEFAULT_DIVIDER,
                mode != null ? mode.viewMode() : UiViewMode.SIMPLE);
    }

    private void applyRestoredGeometry(WindowGeometry geometry) {
        ViewModeController mode = viewModeController.get();
        mode.restoreMode(geometry.viewMode(), mainView::simpleModeButton, mainView::extendedModeButton);
        mode.apply();
        mode.applyDivider(geometry.divider());
    }

    WindowGeometry captureGeometry(Stage stage) {
        ViewModeController mode = viewModeController.get();
        double divider = mode.dividerForSave();
        UiViewMode viewMode = mode.viewMode();
        if (stage.isMaximized() || stage.isFullScreen()) {
            if (lastFloatingGeometry != null) {
                return new WindowGeometry(
                        lastFloatingGeometry.x(),
                        lastFloatingGeometry.y(),
                        lastFloatingGeometry.width(),
                        lastFloatingGeometry.height(),
                        divider,
                        viewMode);
            }
            return new WindowGeometry(
                    Double.NaN,
                    Double.NaN,
                    WindowGeometry.DEFAULT_SIMPLE_WIDTH,
                    WindowGeometry.DEFAULT_SIMPLE_HEIGHT,
                    divider,
                    viewMode);
        }
        WindowGeometry current =
                new WindowGeometry(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), divider, viewMode);
        lastFloatingGeometry = current;
        return current;
    }

    static Rectangle2D visualBoundsFor(WindowGeometry geometry) {
        double cx = Double.isNaN(geometry.x()) ? Double.NaN : geometry.x() + geometry.width() / 2.0;
        double cy = Double.isNaN(geometry.y()) ? Double.NaN : geometry.y() + geometry.height() / 2.0;
        if (!Double.isNaN(cx) && !Double.isNaN(cy)) {
            for (Screen screen : Screen.getScreens()) {
                Rectangle2D bounds = screen.getVisualBounds();
                if (bounds.contains(cx, cy)) {
                    return bounds;
                }
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }
}
