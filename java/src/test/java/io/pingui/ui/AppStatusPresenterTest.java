package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.MonitorFixtures;
import io.pingui.monitor.MonitorService;
import io.pingui.ui.view.StatusPanel;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.control.ProgressBar;
import org.junit.jupiter.api.Test;

class AppStatusPresenterTest {
    @Test
    void servicesReadyPaintsMonitoringSummary() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            MonitorService monitor = MonitorFixtures.idle();
            monitor.addHost("8.8.8.8", true);
            monitor.addHost("1.1.1.1", false);
            StatusPanel panel = new StatusPanel();
            AppStatusPresenter presenter = new AppStatusPresenter(panel, () -> monitor);
            presenter.setBootstrap("loading");
            presenter.onServicesReady();
            String text = presenter.monitoringText();
            assertTrue(text.contains("1"));
            assertTrue(text.contains("2"));
            assertFalse("loading".equals(text));
            monitor.close();
        });
    }

    @Test
    void beginProgressShowsBarAndCancelInvokesCallback() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            StatusPanel panel = new StatusPanel();
            AppStatusPresenter presenter = new AppStatusPresenter(panel, () -> null);
            AtomicBoolean cancelled = new AtomicBoolean();
            presenter.beginProgress("exporting…", () -> cancelled.set(true));
            assertTrue(panel.progressRow().isVisible());
            assertEquals(ProgressBar.INDETERMINATE_PROGRESS, panel.progressBar().getProgress(), 0.001);
            assertEquals("exporting…", presenter.opsText());
            panel.cancelButton().fire();
            assertTrue(cancelled.get());
            presenter.endProgress();
            assertFalse(panel.progressRow().isVisible());
        });
    }

    @Test
    void transientOpsDoNotWipeMonitoring() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            StatusPanel panel = new StatusPanel();
            AppStatusPresenter presenter = new AppStatusPresenter(panel, () -> null);
            presenter.onServicesReady();
            String monitoring = presenter.monitoringText();
            presenter.showTransient("profile loaded");
            assertEquals(monitoring, presenter.monitoringText());
            assertEquals("profile loaded", presenter.opsText());
        });
    }
}
