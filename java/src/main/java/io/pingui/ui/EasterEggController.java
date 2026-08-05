package io.pingui.ui;

import io.pingui.ui.view.MainView;
import java.util.function.BooleanSupplier;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/** Host-input easter egg: temporary Extended canvas message and auto-dismiss timer. */
final class EasterEggController {
    private static final Duration EASTER_EGG_DURATION = Duration.seconds(30);

    private UiViewMode viewModeBeforeEasterEgg = UiViewMode.SIMPLE;
    private boolean active;
    private PauseTransition timer;

    private final MainView mainView;
    private final ViewModeController viewModeController;
    private final RouteGraphPresenter routeGraphPresenter;
    private final Runnable ensureExtendedStageGeometry;

    EasterEggController(
            MainView mainView,
            ViewModeController viewModeController,
            RouteGraphPresenter routeGraphPresenter,
            Runnable ensureExtendedStageGeometry) {
        this.mainView = mainView;
        this.viewModeController = viewModeController;
        this.routeGraphPresenter = routeGraphPresenter;
        this.ensureExtendedStageGeometry = ensureExtendedStageGeometry;
    }

    boolean isActive() {
        return active;
    }

    BooleanSupplier activeSupplier() {
        return () -> active;
    }

    void start() {
        if (!HostViewRules.matches(mainView.hostInput().getText())) {
            return;
        }
        if (!active) {
            active = true;
            viewModeBeforeEasterEgg = viewModeController.viewMode();
            if (!viewModeController.isExtended()) {
                viewModeController.forceExtended(mainView::extendedModeButton);
                ensureExtendedStageGeometry.run();
            }
        }
        showCanvas();
        restartTimer();
    }

    void showCanvas() {
        String message = HostViewRules.messageFor(mainView.hostInput().getText().strip());
        if (message != null) {
            routeGraphPresenter.showStaticMessage(message);
        }
    }

    void dismiss() {
        if (!active) {
            return;
        }
        active = false;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        viewModeController.restoreMode(
                viewModeBeforeEasterEgg, mainView::simpleModeButton, mainView::extendedModeButton);
    }

    private void restartTimer() {
        if (timer != null) {
            timer.stop();
        }
        timer = new PauseTransition(EASTER_EGG_DURATION);
        timer.setOnFinished(e -> dismiss());
        timer.play();
    }
}
